package br.com.nomar.controlai.application.budget.application

import br.com.nomar.controlai.application.budget.entrypoint.database.model.BudgetPaymentPeriodModel
import br.com.nomar.controlai.application.budget.entrypoint.database.repository.BudgetRepository
import br.com.nomar.controlai.domain.budget.entity.BudgetPaymentPeriod
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.YearMonth

@Component
class BudgetPeriodReplicationWriter(
    private val budgetRepository: BudgetRepository,
    private val budgetPeriodReplicator: BudgetPeriodReplicator,
) {

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun replicateOne(
        futureBudgetId: Long,
        currentYearMonth: YearMonth,
        futureYearMonth: YearMonth,
        sourcePeriods: List<BudgetPaymentPeriod>,
    ) {
        val futureBudget = budgetRepository.findById(futureBudgetId)
            .orElseThrow { NoSuchElementException("Future budget not found: $futureBudgetId") }

        val existingByPaymentMethodId = futureBudget.paymentPeriods.associateBy { it.paymentMethodId }

        sourcePeriods.forEach { source ->
            val (newStart, newEnd) = budgetPeriodReplicator.shift(currentYearMonth, futureYearMonth, source.startDate, source.endDate)
            val existing = existingByPaymentMethodId[source.paymentMethodId]
            if (existing != null) {
                existing.startDate = newStart
                existing.endDate = newEnd
            } else {
                futureBudget.paymentPeriods.add(
                    BudgetPaymentPeriodModel(
                        budget = futureBudget,
                        paymentMethodId = source.paymentMethodId,
                        startDate = newStart,
                        endDate = newEnd,
                    )
                )
            }
        }

        budgetRepository.save(futureBudget)
    }
}
