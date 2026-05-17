package br.com.nomar.controlai.application.payments_notification.entrypoint.rest.response

import br.com.nomar.controlai.application.installments.entrypoint.rest.response.InstallmentResponse
import br.com.nomar.controlai.application.payments_notification.entrypoint.database.model.PaymentNotification
import java.math.BigDecimal
import java.time.LocalDateTime

data class PaymentNotificationResponse(
    val id: Long,
    val cardLastDigits: String? = null,
    val purchasedAt: LocalDateTime,
    val amount: BigDecimal,
    val merchantName: String,
    val numberOfInstallments: Int,
    val currentInstallmentNumber: Int? = null,
    val category: String? = null,
    val categoryId: Long? = null,
    val paymentMethodId: Long? = null,
    val subCardId: Long? = null,
    val description: String? = null,
    val origin: String? = null,
    val originType: String? = null,
    val cancelledAt: String? = null,
    val installments: List<InstallmentResponse> = emptyList(),
) {
    companion object {
        fun from(entity: PaymentNotification) = PaymentNotificationResponse(
            id = entity.id,
            cardLastDigits = entity.cardLastDigits,
            purchasedAt = entity.purchasedAt,
            amount = entity.amount,
            merchantName = entity.merchantName,
            numberOfInstallments = entity.numberOfInstallments,
            currentInstallmentNumber = entity.currentInstallmentNumber,
            category = entity.category,
            categoryId = entity.categoryId,
            paymentMethodId = entity.paymentMethodId,
            subCardId = entity.subCardId,
            description = entity.description,
            origin = entity.origin,
            originType = entity.originType,
            cancelledAt = entity.cancelledAt?.toString(),
        )
    }
}
