package br.com.nomar.controlai.application.budget.application

import br.com.nomar.controlai.application.budget.converter.BudgetConverter
import br.com.nomar.controlai.application.budget.entrypoint.database.model.BudgetIncomeModel
import br.com.nomar.controlai.application.budget.entrypoint.database.model.BudgetItemModel
import br.com.nomar.controlai.application.budget.entrypoint.database.model.BudgetModel
import br.com.nomar.controlai.application.budget.entrypoint.database.repository.BudgetRepository
import br.com.nomar.controlai.domain.budget.entity.Budget
import br.com.nomar.controlai.domain.budget.gateway.DuplicateBudgetGateway
import org.springframework.stereotype.Component
import java.time.YearMonth

@Component
class DuplicateBudgetProvider(
    private val budgetRepository: BudgetRepository,
    private val converter: BudgetConverter,
) : DuplicateBudgetGateway {

    override fun execute(sourceBudgetId: Long, targetYearMonth: YearMonth): Result<Budget> {
        return runCatching {
            val source = budgetRepository.findById(sourceBudgetId)
                .orElseThrow { NoSuchElementException("Source budget not found: $sourceBudgetId") }

            if (budgetRepository.findByYearMonth(targetYearMonth.toString()).isPresent) {
                throw IllegalStateException("Budget for $targetYearMonth already exists.")
            }

            val newBudget = BudgetModel(yearMonth = targetYearMonth.toString())

            newBudget.items.addAll(source.items.map { item ->
                BudgetItemModel(
                    budget = newBudget,
                    categoryId = item.categoryId,
                    type = item.type,
                    expected = item.expected,
                )
            })

            newBudget.incomes.addAll(source.incomes.map { income ->
                BudgetIncomeModel(
                    budget = newBudget,
                    label = income.label,
                    amount = income.amount,
                )
            })

            val saved = budgetRepository.save(newBudget)
            converter.toEntity(saved)
        }
    }
}
