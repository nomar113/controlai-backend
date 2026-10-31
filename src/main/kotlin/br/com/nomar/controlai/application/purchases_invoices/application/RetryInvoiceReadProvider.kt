package br.com.nomar.controlai.application.purchases_invoices.application

import br.com.nomar.controlai.application.purchases_invoices.entrypoint.database.repository.PurchaseInvoiceRepository
import br.com.nomar.controlai.domain.auth.RequestContext
import br.com.nomar.controlai.domain.purchases_invoices.gateway.RetryInvoiceReadGateway
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class RetryInvoiceReadProvider(
    private val purchaseInvoiceRepository: PurchaseInvoiceRepository,
    private val requestContext: RequestContext,
) : RetryInvoiceReadGateway {

    @Transactional
    override fun execute(invoiceId: Long): Result<Unit> = runCatching {
        val groupId = requestContext.groupId
        // Conditional on READ_FAILED: a concurrent failure registered by the processor on the same row
        // cannot leave an inconsistent state, each side only writes when the status is the one it expects
        if (purchaseInvoiceRepository.requeueReadFailed(invoiceId, groupId) == 0) {
            if (!purchaseInvoiceRepository.existsByIdAndGroupId(invoiceId, groupId)) {
                throw NoSuchElementException("PurchaseInvoice not found: $invoiceId")
            }
            throw IllegalStateException("PurchaseInvoice is not READ_FAILED: $invoiceId")
        }
    }
}
