package br.com.nomar.controlai.application.payment_methods.entrypoint.rest.request

import jakarta.validation.constraints.NotBlank

data class CreateHolderRequest(
    @field:NotBlank
    val name: String,
)
