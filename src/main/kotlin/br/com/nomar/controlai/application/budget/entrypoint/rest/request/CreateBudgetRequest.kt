package br.com.nomar.controlai.application.budget.entrypoint.rest.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern

data class CreateBudgetRequest(
    @field:NotBlank
    @field:Pattern(regexp = "\\d{4}-\\d{2}", message = "Formato deve ser YYYY-MM")
    val yearMonth: String,
)
