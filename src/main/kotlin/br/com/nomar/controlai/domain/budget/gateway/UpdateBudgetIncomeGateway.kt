package br.com.nomar.controlai.domain.budget.gateway

import br.com.nomar.controlai.domain.budget.entity.BudgetIncome

fun interface UpdateBudgetIncomeGateway {
    fun execute(income: BudgetIncome): Result<BudgetIncome>
}
