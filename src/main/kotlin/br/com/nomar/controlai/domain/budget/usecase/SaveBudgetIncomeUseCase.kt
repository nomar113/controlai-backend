package br.com.nomar.controlai.domain.budget.usecase

import br.com.nomar.controlai.domain.budget.entity.BudgetIncome
import br.com.nomar.controlai.domain.budget.gateway.SaveBudgetIncomeGateway
import org.springframework.stereotype.Component

@Component
class SaveBudgetIncomeUseCase(
    private val saveBudgetIncomeGateway: SaveBudgetIncomeGateway,
) {
    fun execute(income: BudgetIncome): Result<BudgetIncome> {
        return runCatching {
            saveBudgetIncomeGateway.execute(income).getOrThrow()
        }
    }
}
