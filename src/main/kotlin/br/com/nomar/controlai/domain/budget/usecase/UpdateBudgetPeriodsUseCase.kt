package br.com.nomar.controlai.domain.budget.usecase

import br.com.nomar.controlai.domain.budget.entity.BudgetPaymentPeriod
import br.com.nomar.controlai.domain.budget.gateway.UpdateBudgetPeriodsGateway
import org.springframework.stereotype.Component

@Component
class UpdateBudgetPeriodsUseCase(
    private val updateBudgetPeriodsGateway: UpdateBudgetPeriodsGateway,
) {
    fun execute(budgetId: Long, periods: List<BudgetPaymentPeriod>): Result<Unit> {
        return runCatching {
            updateBudgetPeriodsGateway.execute(budgetId, periods).getOrThrow()
        }
    }
}
