package br.com.nomar.controlai.domain.budget.gateway

import br.com.nomar.controlai.domain.budget.entity.BudgetSummary
import java.time.YearMonth

fun interface GetBudgetSummaryGateway {
    fun execute(yearMonth: YearMonth): Result<BudgetSummary>
}
