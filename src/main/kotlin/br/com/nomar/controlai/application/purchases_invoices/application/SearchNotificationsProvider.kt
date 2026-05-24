package br.com.nomar.controlai.application.purchases_invoices.application

import br.com.nomar.controlai.application.payments_notification.entrypoint.database.repository.PaymentNotificationRepository
import br.com.nomar.controlai.application.purchases_invoices.entrypoint.database.repository.PurchaseInvoiceRepository
import br.com.nomar.controlai.application.suggestion.entrypoint.rest.response.SuggestionResponse
import br.com.nomar.controlai.domain.purchases_invoices.gateway.SearchNotificationsGateway
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.LocalDateTime

@Component
class SearchNotificationsProvider(
    private val purchaseInvoiceRepository: PurchaseInvoiceRepository,
    private val paymentNotificationRepository: PaymentNotificationRepository,
) : SearchNotificationsGateway {

    override fun execute(
        invoiceId: Long,
        amount: BigDecimal?,
        startDate: LocalDateTime?,
        endDate: LocalDateTime?,
    ): Result<List<SuggestionResponse>> {
        return runCatching {
            val invoice = purchaseInvoiceRepository.findById(invoiceId)
                .orElseThrow { NoSuchElementException("PurchaseInvoice not found: $invoiceId") }

            if (invoice.deletedAt != null) {
                throw NoSuchElementException("PurchaseInvoice is deleted: $invoiceId")
            }

            if (invoice.cancelledAt != null) {
                throw NoSuchElementException("PurchaseInvoice is cancelled: $invoiceId")
            }

            val effectiveStartDate = when {
                amount == null && startDate == null && endDate == null -> LocalDateTime.now().minusDays(7)
                else -> startDate
            }

            val notifications = paymentNotificationRepository.searchNotifications(
                invoiceId = invoiceId,
                amount = amount,
                startDate = effectiveStartDate,
                endDate = endDate,
            )

            notifications.map { notification ->
                SuggestionResponse(
                    id = notification.id,
                    cardLastDigits = notification.cardLastDigits,
                    purchasedAt = notification.purchasedAt,
                    amount = notification.amount,
                    merchantName = notification.merchantName,
                    numberOfInstallments = notification.numberOfInstallments,
                    category = notification.category,
                    categoryId = notification.categoryId,
                    origin = notification.origin,
                    originType = notification.originType,
                    timeDeltaMinutes = 0,
                )
            }
        }
    }
}
