package br.com.nomar.controlai.domain.budget.gateway

fun interface DeleteBudgetItemGateway {
    fun execute(id: Long): Result<Unit>
}
