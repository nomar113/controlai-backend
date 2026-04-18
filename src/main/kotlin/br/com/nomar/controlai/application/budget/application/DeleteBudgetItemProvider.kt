package br.com.nomar.controlai.application.budget.application

import br.com.nomar.controlai.application.budget.entrypoint.database.repository.BudgetItemRepository
import br.com.nomar.controlai.domain.budget.gateway.DeleteBudgetItemGateway
import org.springframework.stereotype.Component

@Component
class DeleteBudgetItemProvider(
    private val budgetItemRepository: BudgetItemRepository,
) : DeleteBudgetItemGateway {

    override fun execute(id: Long): Result<Unit> {
        return runCatching {
            if (!budgetItemRepository.existsById(id)) {
                throw NoSuchElementException("Budget item not found: $id")
            }
            budgetItemRepository.deleteById(id)
        }
    }
}
