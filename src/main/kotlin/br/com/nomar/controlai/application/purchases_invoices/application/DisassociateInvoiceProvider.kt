package br.com.nomar.controlai.application.purchases_invoices.application

import br.com.nomar.controlai.application.payments_notification.entrypoint.database.repository.PaymentNotificationRepository
import br.com.nomar.controlai.application.purchases_invoices.entrypoint.database.repository.PurchaseInvoiceRepository
import br.com.nomar.controlai.domain.auth.RequestContext
import br.com.nomar.controlai.domain.purchases_invoices.gateway.DisassociateInvoiceGateway
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class DisassociateInvoiceProvider(
    private val purchaseInvoiceRepository: PurchaseInvoiceRepository,
    private val paymentNotificationRepository: PaymentNotificationRepository,
    private val requestContext: RequestContext,
) : DisassociateInvoiceGateway {

    @Transactional
    override fun execute(invoiceId: Long): Result<Unit> {
        return runCatching {
            val invoice = purchaseInvoiceRepository.findByIdAndGroupId(invoiceId, requestContext.groupId)
                ?: throw NoSuchElementException("PurchaseInvoice not found: $invoiceId")

            if (invoice.cancelledAt != null) {
                throw NoSuchElementException("PurchaseInvoice is cancelled: $invoiceId")
            }

            val notification = paymentNotificationRepository.findByPurchaseInvoiceId(invoiceId)
                ?: return@runCatching

            notification.purchaseInvoiceId = null
            paymentNotificationRepository.save(notification)
        }
    }
}
