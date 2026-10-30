package br.com.nomar.controlai.domain.purchases_invoices.usecase

import br.com.nomar.controlai.domain.purchases_invoices.entity.FailureOutcome
import br.com.nomar.controlai.domain.purchases_invoices.entity.InvoiceReadFailureReason
import br.com.nomar.controlai.domain.purchases_invoices.entity.value_objects.AccessKey
import br.com.nomar.controlai.domain.purchases_invoices.gateway.RegisterInvoiceReadFailureGateway
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

// Counts a failed read reported by the NFC-e processor; the backend owns the attempts limit
@Component
class RegisterInvoiceReadFailureUseCase(
    private val registerInvoiceReadFailureGateway: RegisterInvoiceReadFailureGateway,
    private val meterRegistry: MeterRegistry,
    @Value("\${nfce.processor.max-read-attempts:3}") private val maxReadAttempts: Int,
) {

    init {
        require(maxReadAttempts >= 1) { "nfce.processor.max-read-attempts must be at least 1" }
    }

    // Failure: IllegalArgumentException when accessKey is not a 44-digit key
    fun execute(invoiceId: Long, accessKey: String, reason: InvoiceReadFailureReason): Result<FailureOutcome> {
        val key = runCatching { AccessKey.of(accessKey) }.getOrElse { return Result.failure(it) }

        // Never log the URL or the access key: the SEFAZ page may show the consumer's CPF
        return registerInvoiceReadFailureGateway.execute(invoiceId, key, reason, maxReadAttempts)
            .onSuccess { result ->
                if (result.outcome == FailureOutcome.Outcome.DISCARDED) {
                    logger.info("Invoice read discarded: invoiceId={}, reason=not pending or access key mismatch", invoiceId)
                } else {
                    logger.warn("Invoice read failure: invoiceId={}, reason={}, attempts={}", invoiceId, reason, result.attempts)
                }
                recordMetric(result.outcome)
            }
            .onFailure { ex -> logger.error("Failed to register invoice read failure: invoiceId={}", invoiceId, ex) }
    }

    private fun recordMetric(outcome: FailureOutcome.Outcome) {
        val tag = when (outcome) {
            FailureOutcome.Outcome.RETRY_SCHEDULED -> InvoiceReadMetric.FAILED
            FailureOutcome.Outcome.READ_FAILED -> InvoiceReadMetric.READ_FAILED
            FailureOutcome.Outcome.DISCARDED -> InvoiceReadMetric.DISCARDED
        }
        meterRegistry.counter(InvoiceReadMetric.NAME, InvoiceReadMetric.TAG, tag).increment()
    }

    companion object {
        private val logger = LoggerFactory.getLogger(RegisterInvoiceReadFailureUseCase::class.java)
    }
}
