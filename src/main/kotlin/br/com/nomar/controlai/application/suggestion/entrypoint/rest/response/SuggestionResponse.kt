package br.com.nomar.controlai.application.suggestion.entrypoint.rest.response

import br.com.nomar.controlai.application.payments_notification.entrypoint.database.model.PaymentNotification
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.abs

data class SuggestionResponse(
    val id: Long,
    val cardLastDigits: String?,
    val purchasedAt: LocalDateTime,
    val amount: BigDecimal,
    val merchantName: String,
    val numberOfInstallments: Int,
    val category: String?,
    val categoryId: Long?,
    val origin: String?,
    val originType: String?,
    val timeDeltaMinutes: Long,
) {
    companion object {
        fun from(notification: PaymentNotification, invoiceDate: OffsetDateTime): SuggestionResponse {
            val invoiceLocalDateTime = invoiceDate.toLocalDateTime()
            val deltaMinutes = abs(ChronoUnit.MINUTES.between(notification.purchasedAt, invoiceLocalDateTime))

            return SuggestionResponse(
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
                timeDeltaMinutes = deltaMinutes,
            )
        }
    }
}
