package br.com.nomar.controlai.application.purchases_invoices.application

import br.com.nomar.controlai.application.purchases_invoices.entrypoint.database.repository.InvoiceNoticeRepository
import br.com.nomar.controlai.application.purchases_invoices.entrypoint.database.repository.PurchaseInvoiceRepository
import br.com.nomar.controlai.domain.auth.RequestContext
import br.com.nomar.controlai.domain.purchases_invoices.gateway.MarkInvoiceViewedGateway
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class MarkInvoiceViewedProvider(
    private val purchaseInvoiceRepository: PurchaseInvoiceRepository,
    private val invoiceNoticeRepository: InvoiceNoticeRepository,
    private val requestContext: RequestContext,
) : MarkInvoiceViewedGateway {

    @Transactional
    override fun execute(invoiceId: Long): Result<Unit> = runCatching {
        if (!purchaseInvoiceRepository.existsByIdAndGroupId(invoiceId, requestContext.groupId)) {
            throw NoSuchElementException("PurchaseInvoice not found: $invoiceId")
        }
        // INSERT IGNORE: viewing the same invoice again is not an error
        invoiceNoticeRepository.markViewed(requestContext.userId, invoiceId)
        Unit
    }
}
