package br.com.nomar.controlai.application.budget.application

import br.com.nomar.controlai.application.budget.entrypoint.database.repository.BudgetItemRepository
import br.com.nomar.controlai.domain.auth.RequestContext
import br.com.nomar.controlai.domain.budget.gateway.DeleteBudgetItemGateway
import org.springframework.stereotype.Component

@Component
class DeleteBudgetItemProvider(
    private val budgetItemRepository: BudgetItemRepository,
    private val requestContext: RequestContext,
) : DeleteBudgetItemGateway {

    override fun execute(id: Long): Result<Unit> {
        return runCatching {
            val existing = budgetItemRepository.findById(id)
                .orElseThrow { NoSuchElementException("Budget item not found: $id") }
            if (existing.budget?.groupId != requestContext.groupId) {
                throw NoSuchElementException("Budget item not found: $id")
            }
            budgetItemRepository.deleteById(id)
        }
    }
}
