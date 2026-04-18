package br.com.nomar.controlai.domain.budget.usecase

import br.com.nomar.controlai.domain.budget.entity.BudgetIncome
import br.com.nomar.controlai.domain.budget.gateway.UpdateBudgetIncomeGateway
import org.springframework.stereotype.Component

@Component
class UpdateBudgetIncomeUseCase(
    private val updateBudgetIncomeGateway: UpdateBudgetIncomeGateway,
) {
    fun execute(income: BudgetIncome): Result<BudgetIncome> {
        return runCatching {
            updateBudgetIncomeGateway.execute(income).getOrThrow()
        }
    }
}
