package br.com.nomar.controlai.domain.budget.usecase

import br.com.nomar.controlai.domain.budget.entity.BudgetItem
import br.com.nomar.controlai.domain.budget.gateway.SaveBudgetItemGateway
import org.springframework.stereotype.Component

@Component
class SaveBudgetItemUseCase(
    private val saveBudgetItemGateway: SaveBudgetItemGateway,
) {
    fun execute(item: BudgetItem): Result<BudgetItem> {
        return runCatching {
            saveBudgetItemGateway.execute(item).getOrThrow()
        }
    }
}
