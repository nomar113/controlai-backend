package br.com.nomar.controlai.application.payments_notification.application

import br.com.nomar.controlai.application.payments_notification.entrypoint.database.repository.PaymentNotificationRepository
import br.com.nomar.controlai.application.payments_notification.entrypoint.rest.response.InvoiceSuggestionResponse
import br.com.nomar.controlai.application.purchases_invoices.entrypoint.database.repository.PurchaseInvoiceRepository
import br.com.nomar.controlai.domain.auth.RequestContext
import org.springframework.stereotype.Component

@Component
class FindNotificationInvoiceSuggestionsProvider(
    private val paymentNotificationRepository: PaymentNotificationRepository,
    private val purchaseInvoiceRepository: PurchaseInvoiceRepository,
    private val requestContext: RequestContext,
) {

    fun execute(notificationId: Long): Result<List<InvoiceSuggestionResponse>> {
        return runCatching {
            val groupId = requestContext.groupId
            val notification = paymentNotificationRepository.findByIdAndGroupId(notificationId, groupId)
                ?: throw NoSuchElementException("PaymentNotification not found: $notificationId")

            purchaseInvoiceRepository.findByTotalAndNotAssociated(
                amount = notification.amount,
                purchasedAt = notification.purchasedAt,
                groupId = groupId,
            ).map { InvoiceSuggestionResponse.from(it) }
        }
    }
}
