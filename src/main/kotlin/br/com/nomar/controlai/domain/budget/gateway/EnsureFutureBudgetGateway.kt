package br.com.nomar.controlai.domain.budget.gateway

import java.time.YearMonth

fun interface EnsureFutureBudgetGateway {
    fun execute(groupId: Long, yearMonth: YearMonth): Result<Long>
}
