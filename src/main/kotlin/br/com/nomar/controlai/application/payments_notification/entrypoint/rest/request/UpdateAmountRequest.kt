package br.com.nomar.controlai.application.payments_notification.entrypoint.rest.request

import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.NotNull
import java.math.BigDecimal

data class UpdateAmountRequest(
    // Negative amounts are allowed to register card refunds/chargebacks (estorno).
    @field:NotNull
    val amount: BigDecimal,
) {
    @get:AssertTrue(message = "amount must not be zero")
    val amountIsNonZero: Boolean
        get() = amount.compareTo(BigDecimal.ZERO) != 0
}
