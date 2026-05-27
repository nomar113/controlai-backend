package br.com.nomar.controlai.application.payments_notification.application

import br.com.nomar.controlai.application.payments_notification.entrypoint.database.repository.PaymentNotificationRepository
import br.com.nomar.controlai.application.payments_notification.entrypoint.rest.response.InvoiceSuggestionResponse
import br.com.nomar.controlai.application.purchases_invoices.entrypoint.database.repository.PurchaseInvoiceRepository
import org.springframework.stereotype.Component

@Component
class FindNotificationInvoiceSuggestionsProvider(
    private val paymentNotificationRepository: PaymentNotificationRepository,
    private val purchaseInvoiceRepository: PurchaseInvoiceRepository,
) {

    fun execute(notificationId: Long): Result<List<InvoiceSuggestionResponse>> {
        return runCatching {
            val notification = paymentNotificationRepository.findById(notificationId)
                .orElseThrow { NoSuchElementException("PaymentNotification not found: $notificationId") }

            purchaseInvoiceRepository.findByTotalAndNotAssociated(
                amount = notification.amount,
                purchasedAt = notification.purchasedAt,
            ).map { InvoiceSuggestionResponse.from(it) }
        }
    }
}
