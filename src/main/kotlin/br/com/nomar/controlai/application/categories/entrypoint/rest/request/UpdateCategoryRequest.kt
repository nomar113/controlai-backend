package br.com.nomar.controlai.application.categories.entrypoint.rest.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class UpdateCategoryRequest(
    @field:NotBlank
    @field:Size(max = 50)
    val name: String,
)
