package br.com.nomar.controlai.application.budget.entrypoint.rest.request

import jakarta.validation.constraints.NotNull
import java.math.BigDecimal

data class UpdateBudgetItemRequest(
    @field:NotNull
    val expected: BigDecimal,
)
