package br.com.nomar.controlai.application.budget.application

import br.com.nomar.controlai.application.budget.entrypoint.database.repository.BudgetIncomeRepository
import br.com.nomar.controlai.domain.auth.RequestContext
import br.com.nomar.controlai.domain.budget.gateway.DeleteBudgetIncomeGateway
import org.springframework.stereotype.Component

@Component
class DeleteBudgetIncomeProvider(
    private val budgetIncomeRepository: BudgetIncomeRepository,
    private val requestContext: RequestContext,
) : DeleteBudgetIncomeGateway {

    override fun execute(id: Long): Result<Unit> {
        return runCatching {
            val existing = budgetIncomeRepository.findById(id)
                .orElseThrow { NoSuchElementException("Budget income not found: $id") }
            if (existing.budget?.groupId != requestContext.groupId) {
                throw NoSuchElementException("Budget income not found: $id")
            }
            budgetIncomeRepository.deleteById(id)
        }
    }
}
