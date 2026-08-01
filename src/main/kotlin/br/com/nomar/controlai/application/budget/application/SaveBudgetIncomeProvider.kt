package br.com.nomar.controlai.application.budget.application

import br.com.nomar.controlai.application.budget.converter.BudgetConverter
import br.com.nomar.controlai.application.budget.entrypoint.database.repository.BudgetIncomeRepository
import br.com.nomar.controlai.application.budget.entrypoint.database.repository.BudgetRepository
import br.com.nomar.controlai.domain.auth.RequestContext
import br.com.nomar.controlai.domain.budget.entity.BudgetIncome
import br.com.nomar.controlai.domain.budget.gateway.SaveBudgetIncomeGateway
import org.springframework.stereotype.Component

@Component
class SaveBudgetIncomeProvider(
    private val budgetIncomeRepository: BudgetIncomeRepository,
    private val budgetRepository: BudgetRepository,
    private val converter: BudgetConverter,
    private val requestContext: RequestContext,
) : SaveBudgetIncomeGateway {

    override fun execute(income: BudgetIncome): Result<BudgetIncome> {
        return runCatching {
            val budget = budgetRepository.findByIdAndGroupId(income.budgetId, requestContext.groupId)
                ?: throw NoSuchElementException("Budget not found: ${income.budgetId}")
            val model = converter.toIncomeModel(income, budget)
            val saved = budgetIncomeRepository.save(model)
            converter.toIncomeEntity(saved)
        }
    }
}
