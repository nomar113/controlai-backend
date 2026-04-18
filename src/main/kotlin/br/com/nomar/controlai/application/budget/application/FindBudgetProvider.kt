package br.com.nomar.controlai.application.budget.application

import br.com.nomar.controlai.application.budget.converter.BudgetConverter
import br.com.nomar.controlai.application.budget.entrypoint.database.repository.BudgetRepository
import br.com.nomar.controlai.domain.budget.entity.Budget
import br.com.nomar.controlai.domain.budget.gateway.FindBudgetGateway
import org.springframework.stereotype.Component
import java.time.YearMonth

@Component
class FindBudgetProvider(
    private val budgetRepository: BudgetRepository,
    private val converter: BudgetConverter,
) : FindBudgetGateway {

    override fun execute(yearMonth: YearMonth): Result<Budget> {
        return runCatching {
            val model = budgetRepository.findByYearMonth(yearMonth.toString())
                .orElseThrow { NoSuchElementException("Budget not found: $yearMonth") }
            converter.toEntity(model)
        }
    }
}
