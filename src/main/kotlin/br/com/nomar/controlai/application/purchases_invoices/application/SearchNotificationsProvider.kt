package br.com.nomar.controlai.application.purchases_invoices.application

import br.com.nomar.controlai.application.payments_notification.entrypoint.database.repository.PaymentNotificationRepository
import br.com.nomar.controlai.application.purchases_invoices.entrypoint.database.repository.PurchaseInvoiceRepository
import br.com.nomar.controlai.application.suggestion.entrypoint.rest.response.SuggestionResponse
import br.com.nomar.controlai.domain.auth.RequestContext
import br.com.nomar.controlai.domain.purchases_invoices.gateway.SearchNotificationsGateway
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant

@Component
class SearchNotificationsProvider(
    private val purchaseInvoiceRepository: PurchaseInvoiceRepository,
    private val paymentNotificationRepository: PaymentNotificationRepository,
    private val requestContext: RequestContext,
) : SearchNotificationsGateway {

    override fun execute(
        invoiceId: Long,
        amount: BigDecimal?,
        startDate: Instant?,
        endDate: Instant?,
    ): Result<List<SuggestionResponse>> {
        return runCatching {
            val groupId = requestContext.groupId
            val invoice = purchaseInvoiceRepository.findByIdAndGroupId(invoiceId, groupId)
                ?: throw NoSuchElementException("PurchaseInvoice not found: $invoiceId")

            if (invoice.deletedAt != null) {
                throw NoSuchElementException("PurchaseInvoice is deleted: $invoiceId")
            }

            if (invoice.cancelledAt != null) {
                throw NoSuchElementException("PurchaseInvoice is cancelled: $invoiceId")
            }

            val effectiveStartDate = when {
                amount == null && startDate == null && endDate == null -> Instant.now().minus(Duration.ofDays(7))
                else -> startDate
            }

            val notifications = paymentNotificationRepository.searchNotifications(
                invoiceId = invoiceId,
                groupId = groupId,
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
                    category = notification.category?.name,
                    categoryId = notification.category?.id,
                    origin = notification.origin,
                    originType = notification.originType,
                    timeDeltaMinutes = 0,
                )
            }
        }
    }
}
