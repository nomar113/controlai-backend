package br.com.nomar.controlai.application.budget.application

import br.com.nomar.controlai.application.budget.entrypoint.database.repository.BudgetRepository
import br.com.nomar.controlai.domain.auth.RequestContext
import br.com.nomar.controlai.domain.budget.gateway.DeleteBudgetGateway
import org.springframework.stereotype.Component

@Component
class DeleteBudgetProvider(
    private val budgetRepository: BudgetRepository,
    private val requestContext: RequestContext,
) : DeleteBudgetGateway {

    override fun execute(id: Long): Result<Unit> {
        return runCatching {
            budgetRepository.findByIdAndGroupId(id, requestContext.groupId)
                ?: throw NoSuchElementException("Budget not found: $id")
            budgetRepository.deleteById(id)
        }
    }
}
