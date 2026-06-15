package br.com.nomar.controlai.application.payments_notification.entrypoint.rest.request

import jakarta.validation.constraints.NotNull

data class UpdatePaymentMethodRequest(
    @field:NotNull
    val paymentMethodId: Long,
    val subCardId: Long? = null,
)
