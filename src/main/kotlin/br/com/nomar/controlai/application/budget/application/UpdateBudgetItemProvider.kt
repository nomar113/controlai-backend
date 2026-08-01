package br.com.nomar.controlai.application.budget.application

import br.com.nomar.controlai.application.budget.converter.BudgetConverter
import br.com.nomar.controlai.application.budget.entrypoint.database.repository.BudgetItemRepository
import br.com.nomar.controlai.domain.auth.RequestContext
import br.com.nomar.controlai.domain.budget.entity.BudgetItem
import br.com.nomar.controlai.domain.budget.gateway.UpdateBudgetItemGateway
import org.springframework.stereotype.Component

@Component
class UpdateBudgetItemProvider(
    private val budgetItemRepository: BudgetItemRepository,
    private val converter: BudgetConverter,
    private val requestContext: RequestContext,
) : UpdateBudgetItemGateway {

    override fun execute(item: BudgetItem): Result<BudgetItem> {
        return runCatching {
            val existing = budgetItemRepository.findById(item.id!!)
                .orElseThrow { NoSuchElementException("Budget item not found: ${item.id}") }
            if (existing.budget?.groupId != requestContext.groupId) {
                throw NoSuchElementException("Budget item not found: ${item.id}")
            }
            val updated = existing.copy(
                expected = item.expected,
            )
            val saved = budgetItemRepository.save(updated)
            converter.toItemEntity(saved)
        }
    }
}
