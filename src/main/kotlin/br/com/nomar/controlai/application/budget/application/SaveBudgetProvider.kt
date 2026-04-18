package br.com.nomar.controlai.application.budget.application

import br.com.nomar.controlai.application.budget.converter.BudgetConverter
import br.com.nomar.controlai.application.budget.entrypoint.database.repository.BudgetRepository
import br.com.nomar.controlai.domain.budget.entity.Budget
import br.com.nomar.controlai.domain.budget.gateway.SaveBudgetGateway
import org.springframework.stereotype.Component

@Component
class SaveBudgetProvider(
    private val budgetRepository: BudgetRepository,
    private val converter: BudgetConverter,
) : SaveBudgetGateway {

    override fun execute(budget: Budget): Result<Budget> {
        return runCatching {
            val model = converter.toModel(budget)
            val saved = budgetRepository.save(model)
            converter.toEntity(saved)
        }
    }
}
