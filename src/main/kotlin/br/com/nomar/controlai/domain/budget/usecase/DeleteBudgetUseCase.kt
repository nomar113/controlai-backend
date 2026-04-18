package br.com.nomar.controlai.domain.budget.usecase

import br.com.nomar.controlai.domain.budget.gateway.DeleteBudgetGateway
import org.springframework.stereotype.Component

@Component
class DeleteBudgetUseCase(
    private val deleteBudgetGateway: DeleteBudgetGateway,
) {
    fun execute(id: Long): Result<Unit> {
        return runCatching {
            deleteBudgetGateway.execute(id).getOrThrow()
        }
    }
}
