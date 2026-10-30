package br.com.nomar.controlai.application.nfce_processor.application

import br.com.nomar.controlai.application.purchases_invoices.entrypoint.database.repository.PurchaseInvoiceRepository
import br.com.nomar.controlai.domain.purchases_invoices.entity.FailureOutcome
import br.com.nomar.controlai.domain.purchases_invoices.entity.InvoiceReadFailureReason
import br.com.nomar.controlai.domain.purchases_invoices.entity.InvoiceStatus
import br.com.nomar.controlai.domain.purchases_invoices.entity.value_objects.AccessKey
import br.com.nomar.controlai.domain.purchases_invoices.gateway.RegisterInvoiceReadFailureGateway
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate

@Component
class RegisterInvoiceReadFailureProvider(
    private val purchaseInvoiceRepository: PurchaseInvoiceRepository,
    private val transactionTemplate: TransactionTemplate,
) : RegisterInvoiceReadFailureGateway {

    // The conditional update and the read back share a transaction: the updated row stays locked,
    // so the outcome reflects this failure even with concurrent reports for the same invoice
    override fun execute(
        invoiceId: Long,
        accessKey: AccessKey,
        reason: InvoiceReadFailureReason,
        maxAttempts: Int,
    ): Result<FailureOutcome> = runCatching {
        transactionTemplate.execute { register(invoiceId, accessKey, reason, maxAttempts) }!!
    }

    private fun register(
        invoiceId: Long,
        accessKey: AccessKey,
        reason: InvoiceReadFailureReason,
        maxAttempts: Int,
    ): FailureOutcome {
        val updated = purchaseInvoiceRepository.registerReadFailure(invoiceId, accessKey.value, reason.name, maxAttempts)
        if (updated == 0) return FailureOutcome.NOTHING_CHANGED

        val invoice = purchaseInvoiceRepository.findById(invoiceId).orElseThrow()
        val outcome = if (invoice.status == InvoiceStatus.READ_FAILED) {
            FailureOutcome.Outcome.READ_FAILED
        } else {
            FailureOutcome.Outcome.RETRY_SCHEDULED
        }
        return FailureOutcome(outcome, invoice.readAttempts)
    }
}
