package br.com.nomar.controlai.application.budget.entrypoint.rest.request

import jakarta.validation.constraints.NotNull
import java.math.BigDecimal

data class CreateBudgetItemRequest(
    @field:NotNull
    val categoryId: Long,

    @field:NotNull
    val type: String,

    @field:NotNull
    val expected: BigDecimal,
)
