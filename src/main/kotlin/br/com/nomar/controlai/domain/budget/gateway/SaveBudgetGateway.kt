package br.com.nomar.controlai.domain.budget.gateway

import br.com.nomar.controlai.domain.budget.entity.Budget

fun interface SaveBudgetGateway {
    fun execute(budget: Budget): Result<Budget>
}
