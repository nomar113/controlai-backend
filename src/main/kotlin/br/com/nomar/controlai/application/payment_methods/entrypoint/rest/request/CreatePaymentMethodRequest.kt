package br.com.nomar.controlai.application.payment_methods.entrypoint.rest.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull

data class CreatePaymentMethodRequest(
    @field:NotBlank
    val name: String,
    @field:NotBlank
    val type: String,
    @field:NotNull
    val holderId: Long,
    val brand: String? = null,
    val closingDay: Int? = null,
    val subCards: List<CreateSubCardRequest> = emptyList(),
)
