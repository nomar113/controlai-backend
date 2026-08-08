package br.com.nomar.controlai.application.budget.application

import br.com.nomar.controlai.application.budget.entrypoint.database.model.BudgetPaymentPeriodModel
import br.com.nomar.controlai.application.budget.entrypoint.database.repository.BudgetRepository
import br.com.nomar.controlai.domain.auth.RequestContext
import br.com.nomar.controlai.domain.budget.entity.BudgetPaymentPeriod
import br.com.nomar.controlai.domain.budget.gateway.UpdateBudgetPeriodsGateway
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class UpdateBudgetPeriodsProvider(
    private val budgetRepository: BudgetRepository,
    private val requestContext: RequestContext,
) : UpdateBudgetPeriodsGateway {

    @Transactional
    override fun execute(budgetId: Long, periods: List<BudgetPaymentPeriod>): Result<Unit> {
        return runCatching {
            val budget = budgetRepository.findByIdAndGroupId(budgetId, requestContext.groupId)
                ?: throw NoSuchElementException("Budget not found: $budgetId")

            val existingByPaymentMethodId = budget.paymentPeriods.associateBy { it.paymentMethodId }

            periods.forEach { period ->
                val existing = existingByPaymentMethodId[period.paymentMethodId]
                if (existing != null) {
                    existing.startDate = period.startDate
                    existing.endDate = period.endDate
                } else {
                    budget.paymentPeriods.add(
                        BudgetPaymentPeriodModel(
                            budget = budget,
                            paymentMethodId = period.paymentMethodId,
                            startDate = period.startDate,
                            endDate = period.endDate,
                        )
                    )
                }
            }

            budgetRepository.save(budget)
        }
    }
}
