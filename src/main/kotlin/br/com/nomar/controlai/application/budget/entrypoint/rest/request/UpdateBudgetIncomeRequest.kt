package br.com.nomar.controlai.application.budget.entrypoint.rest.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.math.BigDecimal

data class UpdateBudgetIncomeRequest(
    @field:NotBlank
    @field:Size(max = 100)
    val label: String,

    @field:NotNull
    val amount: BigDecimal,
)
