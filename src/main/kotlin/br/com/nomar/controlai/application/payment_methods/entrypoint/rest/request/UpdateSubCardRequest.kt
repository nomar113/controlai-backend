package br.com.nomar.controlai.application.payment_methods.entrypoint.rest.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class UpdateSubCardRequest(
    @field:NotBlank
    @field:Size(min = 4, max = 4)
    val lastFourDigits: String,
    @field:NotBlank
    val type: String,
    val nickname: String? = null,
    val dependentName: String? = null,
    val walletPlatform: String? = null,
)
