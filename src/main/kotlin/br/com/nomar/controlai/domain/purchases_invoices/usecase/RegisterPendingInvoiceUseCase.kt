package br.com.nomar.controlai.domain.purchases_invoices.usecase

import br.com.nomar.controlai.application.purchases_invoices.entrypoint.database.model.PurchaseInvoiceModel
import br.com.nomar.controlai.domain.auth.RequestContext
import br.com.nomar.controlai.domain.purchases_invoices.entity.value_objects.NfceQrCode
import br.com.nomar.controlai.domain.purchases_invoices.exception.DuplicateInvoiceException
import br.com.nomar.controlai.domain.purchases_invoices.gateway.RegisterPendingInvoiceGateway
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Instant

// Stores the scanned NFC-e link as a PENDING invoice; the SEFAZ is never called here
@Component
class RegisterPendingInvoiceUseCase(
    private val registerPendingInvoiceGateway: RegisterPendingInvoiceGateway,
    private val requestContext: RequestContext,
    private val meterRegistry: MeterRegistry,
    private val clock: Clock,
) {

    fun execute(rawContent: String): Result<PurchaseInvoiceModel> {
        val groupId = requestContext.groupId
        val qrCode = try {
            NfceQrCode.parse(rawContent)
        } catch (ex: IllegalArgumentException) {
            // Never log the raw content: it may be a Pix payload carrying personal data
            logger.info("Invalid NFC-e QR Code for group {}: {}", groupId, ex.message)
            recordMetric(RESULT_INVALID, UNKNOWN_UF)
            return Result.failure(ex)
        }

        return registerPendingInvoiceGateway.execute(qrCode, groupId, Instant.now(clock))
            .onSuccess { invoice ->
                logger.info(
                    "Pending invoice registered: groupId={}, invoiceId={}, uf={}, qrVersion={}",
                    groupId, invoice.id, qrCode.uf, qrCode.version,
                )
                recordMetric(RESULT_CREATED, qrCode.uf)
            }
            .onFailure { ex ->
                if (ex is DuplicateInvoiceException) {
                    logger.warn("Duplicate pending invoice: groupId={}, uf={}", groupId, qrCode.uf)
                    recordMetric(RESULT_DUPLICATE, qrCode.uf)
                } else {
                    logger.error("Failed to register pending invoice for group {}", groupId, ex)
                    recordMetric(RESULT_ERROR, qrCode.uf)
                }
            }
    }

    private fun recordMetric(result: String, uf: String) {
        meterRegistry.counter(METRIC_NAME, "result", result, "uf", uf).increment()
    }

    companion object {
        private val logger = LoggerFactory.getLogger(RegisterPendingInvoiceUseCase::class.java)

        const val METRIC_NAME = "controlai.invoices.pending.register"
        const val RESULT_CREATED = "created"
        const val RESULT_DUPLICATE = "duplicate"
        const val RESULT_INVALID = "invalid"
        const val RESULT_ERROR = "error"
        const val UNKNOWN_UF = "unknown"
    }
}
