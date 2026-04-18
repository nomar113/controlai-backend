package br.com.nomar.controlai.domain.budget.usecase

import br.com.nomar.controlai.domain.budget.entity.BudgetItem
import br.com.nomar.controlai.domain.budget.gateway.UpdateBudgetItemGateway
import org.springframework.stereotype.Component

@Component
class UpdateBudgetItemUseCase(
    private val updateBudgetItemGateway: UpdateBudgetItemGateway,
) {
    fun execute(item: BudgetItem): Result<BudgetItem> {
        return runCatching {
            updateBudgetItemGateway.execute(item).getOrThrow()
        }
    }
}
