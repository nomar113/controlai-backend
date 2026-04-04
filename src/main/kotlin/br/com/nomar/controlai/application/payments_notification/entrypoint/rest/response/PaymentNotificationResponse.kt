package br.com.nomar.controlai.application.payments_notification.entrypoint.rest.response

import br.com.nomar.controlai.application.payments_notification.entrypoint.database.model.PaymentNotification
import java.math.BigDecimal
import java.time.LocalDateTime

data class PaymentNotificationResponse(
    val id: Long,
    val cardLastDigits: String,
    val purchasedAt: LocalDateTime,
    val amount: BigDecimal,
    val merchantName: String,
    val numberOfInstallments: Int,
) {
    companion object {
        fun from(entity: PaymentNotification) = PaymentNotificationResponse(
            id = entity.id,
            cardLastDigits = entity.cardLastDigits,
            purchasedAt = entity.purchasedAt,
            amount = entity.amount,
            merchantName = entity.merchantName,
            numberOfInstallments = entity.numberOfInstallments,
        )
    }
}
