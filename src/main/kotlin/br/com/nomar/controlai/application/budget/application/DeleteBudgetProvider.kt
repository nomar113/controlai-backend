package br.com.nomar.controlai.application.budget.application

import br.com.nomar.controlai.application.budget.entrypoint.database.repository.BudgetRepository
import br.com.nomar.controlai.domain.budget.gateway.DeleteBudgetGateway
import org.springframework.stereotype.Component

@Component
class DeleteBudgetProvider(
    private val budgetRepository: BudgetRepository,
) : DeleteBudgetGateway {

    override fun execute(id: Long): Result<Unit> {
        return runCatching {
            if (!budgetRepository.existsById(id)) {
                throw NoSuchElementException("Budget not found: $id")
            }
            budgetRepository.deleteById(id)
        }
    }
}
