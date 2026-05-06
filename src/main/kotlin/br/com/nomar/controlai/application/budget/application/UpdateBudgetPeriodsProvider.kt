package br.com.nomar.controlai.application.budget.application

import br.com.nomar.controlai.application.budget.entrypoint.database.model.BudgetPaymentPeriodModel
import br.com.nomar.controlai.application.budget.entrypoint.database.repository.BudgetRepository
import br.com.nomar.controlai.domain.budget.entity.BudgetPaymentPeriod
import br.com.nomar.controlai.domain.budget.gateway.UpdateBudgetPeriodsGateway
import org.springframework.stereotype.Component

@Component
class UpdateBudgetPeriodsProvider(
    private val budgetRepository: BudgetRepository,
) : UpdateBudgetPeriodsGateway {

    override fun execute(budgetId: Long, periods: List<BudgetPaymentPeriod>): Result<Unit> {
        return runCatching {
            val budget = budgetRepository.findById(budgetId)
                .orElseThrow { NoSuchElementException("Budget not found: $budgetId") }

            budget.paymentPeriods.clear()
            budget.paymentPeriods.addAll(periods.map { period ->
                BudgetPaymentPeriodModel(
                    budget = budget,
                    paymentMethodId = period.paymentMethodId,
                    startDate = period.startDate,
                    endDate = period.endDate,
                )
            })

            budgetRepository.save(budget)
        }
    }
}
