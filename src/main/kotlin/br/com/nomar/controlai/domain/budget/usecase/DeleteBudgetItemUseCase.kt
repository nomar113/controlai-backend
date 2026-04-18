package br.com.nomar.controlai.domain.budget.usecase

import br.com.nomar.controlai.domain.budget.gateway.DeleteBudgetItemGateway
import org.springframework.stereotype.Component

@Component
class DeleteBudgetItemUseCase(
    private val deleteBudgetItemGateway: DeleteBudgetItemGateway,
) {
    fun execute(id: Long): Result<Unit> {
        return runCatching {
            deleteBudgetItemGateway.execute(id).getOrThrow()
        }
    }
}
