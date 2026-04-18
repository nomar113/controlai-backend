package br.com.nomar.controlai.domain.budget.gateway

fun interface DeleteBudgetGateway {
    fun execute(id: Long): Result<Unit>
}
