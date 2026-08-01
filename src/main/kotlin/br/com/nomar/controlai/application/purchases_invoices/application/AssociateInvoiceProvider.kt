package br.com.nomar.controlai.application.purchases_invoices.application

import br.com.nomar.controlai.application.payments_notification.entrypoint.database.repository.PaymentNotificationRepository
import br.com.nomar.controlai.application.purchases_invoices.entrypoint.database.repository.PurchaseInvoiceRepository
import br.com.nomar.controlai.application.purchases_invoices.entrypoint.rest.response.AssociateInvoiceResponse
import br.com.nomar.controlai.domain.auth.RequestContext
import br.com.nomar.controlai.domain.purchases_invoices.gateway.AssociateInvoiceGateway
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Component
class AssociateInvoiceProvider(
    private val purchaseInvoiceRepository: PurchaseInvoiceRepository,
    private val paymentNotificationRepository: PaymentNotificationRepository,
    private val requestContext: RequestContext,
) : AssociateInvoiceGateway {

    @Transactional
    override fun execute(invoiceId: Long, notificationId: Long): Result<AssociateInvoiceResponse> {
        return runCatching {
            val groupId = requestContext.groupId
            val invoice = purchaseInvoiceRepository.findByIdAndGroupId(invoiceId, groupId)
                ?: throw NoSuchElementException("PurchaseInvoice not found: $invoiceId")

            if (invoice.cancelledAt != null) {
                throw NoSuchElementException("PurchaseInvoice is cancelled: $invoiceId")
            }

            val notification = paymentNotificationRepository.findByIdAndGroupId(notificationId, groupId)
                ?: throw NoSuchElementException("PaymentNotification not found: $notificationId")

            if (notification.cancelledAt != null) {
                throw NoSuchElementException("PaymentNotification is cancelled: $notificationId")
            }

            if (notification.purchaseInvoiceId != null && notification.purchaseInvoiceId != invoiceId) {
                throw IllegalStateException("PaymentNotification $notificationId is already associated with invoice ${notification.purchaseInvoiceId}")
            }

            val existingNotification = paymentNotificationRepository.findByPurchaseInvoiceId(invoiceId)
            if (existingNotification != null && existingNotification.id != notificationId) {
                existingNotification.purchaseInvoiceId = null
                paymentNotificationRepository.saveAndFlush(existingNotification)
            }

            notification.purchaseInvoiceId = invoiceId
            paymentNotificationRepository.saveAndFlush(notification)

            AssociateInvoiceResponse(
                invoiceId = invoiceId,
                paymentNotificationId = notificationId,
                associatedAt = LocalDateTime.now(),
            )
        }
    }
}
