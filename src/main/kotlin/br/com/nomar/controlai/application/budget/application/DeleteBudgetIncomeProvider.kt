package br.com.nomar.controlai.application.budget.application

import br.com.nomar.controlai.application.budget.entrypoint.database.repository.BudgetIncomeRepository
import br.com.nomar.controlai.domain.budget.gateway.DeleteBudgetIncomeGateway
import org.springframework.stereotype.Component

@Component
class DeleteBudgetIncomeProvider(
    private val budgetIncomeRepository: BudgetIncomeRepository,
) : DeleteBudgetIncomeGateway {

    override fun execute(id: Long): Result<Unit> {
        return runCatching {
            if (!budgetIncomeRepository.existsById(id)) {
                throw NoSuchElementException("Budget income not found: $id")
            }
            budgetIncomeRepository.deleteById(id)
        }
    }
}
