package br.com.nomar.controlai.application.budget.application

import br.com.nomar.controlai.application.budget.converter.BudgetConverter
import br.com.nomar.controlai.application.budget.entrypoint.database.repository.BudgetIncomeRepository
import br.com.nomar.controlai.domain.auth.RequestContext
import br.com.nomar.controlai.domain.budget.entity.BudgetIncome
import br.com.nomar.controlai.domain.budget.gateway.UpdateBudgetIncomeGateway
import org.springframework.stereotype.Component

@Component
class UpdateBudgetIncomeProvider(
    private val budgetIncomeRepository: BudgetIncomeRepository,
    private val converter: BudgetConverter,
    private val requestContext: RequestContext,
) : UpdateBudgetIncomeGateway {

    override fun execute(income: BudgetIncome): Result<BudgetIncome> {
        return runCatching {
            val existing = budgetIncomeRepository.findById(income.id!!)
                .orElseThrow { NoSuchElementException("Budget income not found: ${income.id}") }
            if (existing.budget?.groupId != requestContext.groupId) {
                throw NoSuchElementException("Budget income not found: ${income.id}")
            }
            val updated = existing.copy(
                label = income.label,
                amount = income.amount,
            )
            val saved = budgetIncomeRepository.save(updated)
            converter.toIncomeEntity(saved)
        }
    }
}
