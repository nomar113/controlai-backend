package br.com.nomar.controlai.domain.budget.gateway

fun interface DeleteBudgetIncomeGateway {
    fun execute(id: Long): Result<Unit>
}
