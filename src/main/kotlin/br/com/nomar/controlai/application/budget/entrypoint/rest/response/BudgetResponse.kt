package br.com.nomar.controlai.application.budget.entrypoint.rest.response

import br.com.nomar.controlai.domain.budget.entity.Budget
import java.math.BigDecimal

data class BudgetResponse(
    val id: Long?,
    val yearMonth: String,
    val items: List<BudgetItemResponse>,
    val incomes: List<BudgetIncomeResponse>,
    val totalExpected: BigDecimal,
    val totalIncome: BigDecimal,
) {
    companion object {
        fun from(entity: Budget) = BudgetResponse(
            id = entity.id,
            yearMonth = entity.yearMonth.toString(),
            items = entity.items.map(BudgetItemResponse::from),
            incomes = entity.incomes.map(BudgetIncomeResponse::from),
            totalExpected = entity.items.sumOf { it.expected },
            totalIncome = entity.incomes.sumOf { it.amount },
        )
    }
}
