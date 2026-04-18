package br.com.nomar.controlai.domain.budget.gateway

import br.com.nomar.controlai.domain.budget.entity.BudgetItem

fun interface SaveBudgetItemGateway {
    fun execute(item: BudgetItem): Result<BudgetItem>
}
