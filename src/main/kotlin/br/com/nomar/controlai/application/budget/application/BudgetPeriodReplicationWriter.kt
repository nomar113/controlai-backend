package br.com.nomar.controlai.application.budget.application

import br.com.nomar.controlai.application.budget.entrypoint.database.model.BudgetPaymentPeriodModel
import br.com.nomar.controlai.application.budget.entrypoint.database.repository.BudgetRepository
import br.com.nomar.controlai.domain.budget.entity.BudgetPaymentPeriod
import jakarta.persistence.EntityManager
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.YearMonth

@Component
class BudgetPeriodReplicationWriter(
    private val budgetRepository: BudgetRepository,
    private val entityManager: EntityManager,
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

        val paymentMethodIds = sourcePeriods.map { it.paymentMethodId }.toSet()
        val unaffected = futureBudget.paymentPeriods
            .filter { it.paymentMethodId !in paymentMethodIds }
            .map { it.paymentMethodId to (it.startDate to it.endDate) }
        val replicated = sourcePeriods.map { source ->
            val (newStart, newEnd) = budgetPeriodReplicator.shift(currentYearMonth, futureYearMonth, source.startDate, source.endDate)
            source.paymentMethodId to (newStart to newEnd)
        }

        futureBudget.paymentPeriods.clear()
        entityManager.flush()

        (unaffected + replicated).forEach { (paymentMethodId, dates) ->
            futureBudget.paymentPeriods.add(
                BudgetPaymentPeriodModel(
                    budget = futureBudget,
                    paymentMethodId = paymentMethodId,
                    startDate = dates.first,
                    endDate = dates.second,
                )
            )
        }
        budgetRepository.save(futureBudget)
    }
}
