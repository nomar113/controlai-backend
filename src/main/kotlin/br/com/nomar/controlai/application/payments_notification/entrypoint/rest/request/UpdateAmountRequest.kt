package br.com.nomar.controlai.application.payments_notification.entrypoint.rest.request

import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotNull
import java.math.BigDecimal

data class UpdateAmountRequest(
    @field:NotNull
    @field:DecimalMin("0.01")
    val amount: BigDecimal,
)
