package br.com.nomar.controlai.domain.budget.gateway

import br.com.nomar.controlai.domain.budget.entity.Budget
import java.time.YearMonth

fun interface FindBudgetGateway {
    fun execute(yearMonth: YearMonth): Result<Budget>
}
