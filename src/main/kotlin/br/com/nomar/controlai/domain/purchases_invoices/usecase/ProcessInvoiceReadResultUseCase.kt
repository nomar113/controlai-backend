package br.com.nomar.controlai.domain.purchases_invoices.usecase

import br.com.nomar.controlai.domain.purchases_invoices.entity.InvoiceReadData
import br.com.nomar.controlai.domain.purchases_invoices.entity.InvoiceReadResult
import br.com.nomar.controlai.domain.purchases_invoices.entity.ReadOutcome
import br.com.nomar.controlai.domain.purchases_invoices.exception.InvoiceReadRejectedException
import br.com.nomar.controlai.domain.purchases_invoices.gateway.ProcessInvoiceReadResultGateway
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

// Turns a PENDING invoice into a complete purchase with the data the NFC-e processor read from the SEFAZ.
// A rejected result changes nothing: the processor reports it back as a failure, which counts the attempt.
@Component
class ProcessInvoiceReadResultUseCase(
    private val processInvoiceReadResultGateway: ProcessInvoiceReadResultGateway,
    private val meterRegistry: MeterRegistry,
) {

    // Failure: InvoiceReadRejectedException when the data is incomplete or inconsistent,
    // IllegalArgumentException when accessKey is not a 44-digit key
    fun execute(invoiceId: Long, data: InvoiceReadData): Result<ReadOutcome> {
        val result = runCatching { InvoiceReadResult.of(data) }.getOrElse { ex ->
            if (ex is InvoiceReadRejectedException) recordRejection(invoiceId, ex)
            return Result.failure(ex)
        }

        // Never log the URL, the access key, the CNPJ or the items
        return processInvoiceReadResultGateway.execute(invoiceId, result)
            .onSuccess { outcome -> recordOutcome(invoiceId, result, outcome) }
            .onFailure { ex -> logger.error("Failed to process invoice read result: invoiceId={}", invoiceId, ex) }
    }

    private fun recordRejection(invoiceId: Long, ex: InvoiceReadRejectedException) {
        logger.warn("Invoice read result rejected: invoiceId={}, reason={}, rule={}", invoiceId, ex.reason, ex.message)
        meterRegistry.counter(InvoiceReadMetric.NAME, InvoiceReadMetric.TAG, InvoiceReadMetric.REJECTED).increment()
    }

    private fun recordOutcome(invoiceId: Long, result: InvoiceReadResult, outcome: ReadOutcome) {
        val tag = when (outcome) {
            ReadOutcome.PROCESSED -> {
                logger.info(
                    "Invoice read processed: invoiceId={}, items={}, payments={}",
                    invoiceId, result.items.size, result.payments.size,
                )
                InvoiceReadMetric.PROCESSED
            }
            ReadOutcome.DISCARDED -> {
                logger.info("Invoice read discarded: invoiceId={}, reason=not pending or access key mismatch", invoiceId)
                InvoiceReadMetric.DISCARDED
            }
        }
        meterRegistry.counter(InvoiceReadMetric.NAME, InvoiceReadMetric.TAG, tag).increment()
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ProcessInvoiceReadResultUseCase::class.java)
    }
}
