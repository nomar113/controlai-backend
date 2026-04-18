package br.com.nomar.controlai.domain.budget.usecase

import br.com.nomar.controlai.domain.budget.entity.Budget
import br.com.nomar.controlai.domain.budget.gateway.SaveBudgetGateway
import org.springframework.stereotype.Component

@Component
class SaveBudgetUseCase(
    private val saveBudgetGateway: SaveBudgetGateway,
) {
    fun execute(budget: Budget): Result<Budget> {
        return runCatching {
            saveBudgetGateway.execute(budget).getOrThrow()
        }
    }
}
