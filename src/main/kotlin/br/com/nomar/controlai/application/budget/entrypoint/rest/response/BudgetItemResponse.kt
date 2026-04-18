package br.com.nomar.controlai.application.budget.entrypoint.rest.response

import br.com.nomar.controlai.domain.budget.entity.BudgetItem
import java.math.BigDecimal

data class BudgetItemResponse(
    val id: Long?,
    val budgetId: Long,
    val categoryId: Long,
    val type: String,
    val expected: BigDecimal,
) {
    companion object {
        fun from(entity: BudgetItem) = BudgetItemResponse(
            id = entity.id,
            budgetId = entity.budgetId,
            categoryId = entity.categoryId,
            type = entity.type.name,
            expected = entity.expected,
        )
    }
}
