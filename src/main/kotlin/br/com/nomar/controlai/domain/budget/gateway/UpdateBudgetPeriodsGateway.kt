package br.com.nomar.controlai.domain.budget.gateway

import br.com.nomar.controlai.domain.budget.entity.BudgetPaymentPeriod

fun interface UpdateBudgetPeriodsGateway {
    fun execute(budgetId: Long, periods: List<BudgetPaymentPeriod>): Result<Unit>
}
