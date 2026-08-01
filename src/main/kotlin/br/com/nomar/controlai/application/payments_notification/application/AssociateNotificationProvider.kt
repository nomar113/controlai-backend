package br.com.nomar.controlai.application.payments_notification.application

import br.com.nomar.controlai.application.payments_notification.entrypoint.database.repository.PaymentNotificationRepository
import br.com.nomar.controlai.application.payments_notification.entrypoint.rest.response.AssociatedInvoiceResponse
import br.com.nomar.controlai.application.payments_notification.entrypoint.rest.response.PaymentNotificationResponse
import br.com.nomar.controlai.application.purchases_invoices.entrypoint.database.repository.PurchaseInvoiceRepository
import br.com.nomar.controlai.domain.auth.RequestContext
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class AssociateNotificationProvider(
    private val paymentNotificationRepository: PaymentNotificationRepository,
    private val purchaseInvoiceRepository: PurchaseInvoiceRepository,
    private val requestContext: RequestContext,
) {

    private val logger = LoggerFactory.getLogger(AssociateNotificationProvider::class.java)

    @Transactional
    fun execute(notificationId: Long, purchaseInvoiceId: Long): Result<PaymentNotificationResponse> {
        return runCatching {
            val groupId = requestContext.groupId
            val notification = paymentNotificationRepository.findByIdAndGroupId(notificationId, groupId)
                ?: throw NoSuchElementException("PaymentNotification not found: $notificationId")

            if (notification.cancelledAt != null) {
                throw NoSuchElementException("PaymentNotification is cancelled: $notificationId")
            }

            val invoice = purchaseInvoiceRepository.findByIdAndGroupId(purchaseInvoiceId, groupId)
                ?: throw NoSuchElementException("PurchaseInvoice not found: $purchaseInvoiceId")

            if (invoice.cancelledAt != null) {
                throw NoSuchElementException("PurchaseInvoice is cancelled: $purchaseInvoiceId")
            }

            if (notification.purchaseInvoiceId != null) {
                logger.warn(
                    "Tentativa de associação duplicada: notificação {} já associada à invoice {}",
                    notificationId,
                    notification.purchaseInvoiceId,
                )
                throw IllegalStateException(
                    "PaymentNotification $notificationId is already associated with invoice ${notification.purchaseInvoiceId}"
                )
            }

            notification.purchaseInvoiceId = purchaseInvoiceId
            val saved = paymentNotificationRepository.saveAndFlush(notification)

            logger.info("Notificação {} associada à invoice {} com sucesso", notificationId, purchaseInvoiceId)

            PaymentNotificationResponse.from(saved).copy(
                associatedInvoice = AssociatedInvoiceResponse.from(invoice),
            )
        }
    }
}
