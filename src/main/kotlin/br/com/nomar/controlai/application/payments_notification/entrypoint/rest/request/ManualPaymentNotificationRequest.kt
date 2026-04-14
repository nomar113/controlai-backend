package br.com.nomar.controlai.application.payments_notification.entrypoint.rest.request

import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.time.LocalDateTime

data class InstallmentOverride(
    val installmentNumber: Int,
    val amount: BigDecimal,
)

data class ManualPaymentNotificationRequest(
    @field:NotBlank
    val merchantName: String,

    @field:NotNull
    @field:DecimalMin("0.01")
    val amount: BigDecimal,

    @field:NotNull
    val purchasedAt: LocalDateTime,

    @field:NotNull
    val paymentMethodId: Long,

    val subCardId: Long? = null,

    @field:Size(min = 4, max = 4)
    val cardLastDigits: String? = null,

    @field:NotNull
    val categoryId: Long? = null,

    @field:Min(1)
    val numberOfInstallments: Int = 1,

    val installments: List<InstallmentOverride>? = null,
)
