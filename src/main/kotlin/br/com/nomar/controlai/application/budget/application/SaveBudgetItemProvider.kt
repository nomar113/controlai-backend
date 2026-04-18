package br.com.nomar.controlai.application.budget.application

import br.com.nomar.controlai.application.budget.converter.BudgetConverter
import br.com.nomar.controlai.application.budget.entrypoint.database.repository.BudgetItemRepository
import br.com.nomar.controlai.application.budget.entrypoint.database.repository.BudgetRepository
import br.com.nomar.controlai.domain.budget.entity.BudgetItem
import br.com.nomar.controlai.domain.budget.gateway.SaveBudgetItemGateway
import org.springframework.stereotype.Component

@Component
class SaveBudgetItemProvider(
    private val budgetItemRepository: BudgetItemRepository,
    private val budgetRepository: BudgetRepository,
    private val converter: BudgetConverter,
) : SaveBudgetItemGateway {

    override fun execute(item: BudgetItem): Result<BudgetItem> {
        return runCatching {
            val budget = budgetRepository.findById(item.budgetId)
                .orElseThrow { NoSuchElementException("Budget not found: ${item.budgetId}") }
            val model = converter.toItemModel(item, budget)
            val saved = budgetItemRepository.save(model)
            converter.toItemEntity(saved)
        }
    }
}
