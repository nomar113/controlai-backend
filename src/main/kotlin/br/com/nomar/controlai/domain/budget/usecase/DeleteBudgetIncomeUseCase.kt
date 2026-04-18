package br.com.nomar.controlai.domain.budget.usecase

import br.com.nomar.controlai.domain.budget.gateway.DeleteBudgetIncomeGateway
import org.springframework.stereotype.Component

@Component
class DeleteBudgetIncomeUseCase(
    private val deleteBudgetIncomeGateway: DeleteBudgetIncomeGateway,
) {
    fun execute(id: Long): Result<Unit> {
        return runCatching {
            deleteBudgetIncomeGateway.execute(id).getOrThrow()
        }
    }
}
