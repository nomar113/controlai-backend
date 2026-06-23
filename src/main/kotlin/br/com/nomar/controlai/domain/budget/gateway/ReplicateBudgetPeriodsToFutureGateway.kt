package br.com.nomar.controlai.domain.budget.gateway

import br.com.nomar.controlai.domain.budget.entity.BudgetPaymentPeriod
import br.com.nomar.controlai.domain.budget.entity.BudgetPeriodReplicationResult

fun interface ReplicateBudgetPeriodsToFutureGateway {
    fun execute(currentBudgetId: Long, periods: List<BudgetPaymentPeriod>): Result<BudgetPeriodReplicationResult>
}
