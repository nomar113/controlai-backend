package br.com.nomar.controlai.application.budget.entrypoint.rest.response

import br.com.nomar.controlai.domain.budget.entity.BudgetIncome
import java.math.BigDecimal

data class BudgetIncomeResponse(
    val id: Long?,
    val budgetId: Long,
    val label: String,
    val amount: BigDecimal,
) {
    companion object {
        fun from(entity: BudgetIncome) = BudgetIncomeResponse(
            id = entity.id,
            budgetId = entity.budgetId,
            label = entity.label,
            amount = entity.amount,
        )
    }
}
