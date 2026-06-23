package br.com.nomar.controlai.application.budget.application

import br.com.nomar.controlai.application.budget.entrypoint.database.repository.BudgetRepository
import br.com.nomar.controlai.domain.budget.entity.BudgetPaymentPeriod
import br.com.nomar.controlai.domain.budget.entity.BudgetPeriodReplicationResult
import br.com.nomar.controlai.domain.budget.entity.FailedReplication
import br.com.nomar.controlai.domain.budget.gateway.ReplicateBudgetPeriodsToFutureGateway
import org.springframework.stereotype.Component
import java.time.YearMonth

@Component
class ReplicateBudgetPeriodsToFutureProvider(
    private val budgetRepository: BudgetRepository,
    private val budgetPeriodReplicationWriter: BudgetPeriodReplicationWriter,
) : ReplicateBudgetPeriodsToFutureGateway {

    override fun execute(
        currentBudgetId: Long,
        periods: List<BudgetPaymentPeriod>,
    ): Result<BudgetPeriodReplicationResult> = runCatching {
        val currentBudget = budgetRepository.findById(currentBudgetId)
            .orElseThrow { NoSuchElementException("Budget not found: $currentBudgetId") }
        val currentYearMonth = YearMonth.parse(currentBudget.yearMonth)

        val futureBudgets = budgetRepository.findAll()
            .filter { runCatching { YearMonth.parse(it.yearMonth) > currentYearMonth }.getOrDefault(false) }
            .sortedBy { it.yearMonth }

        val updated = mutableListOf<Long>()
        val failed = mutableListOf<FailedReplication>()

        futureBudgets.forEach { future ->
            val futureYm = YearMonth.parse(future.yearMonth)
            val futureId = future.id!!
            runCatching {
                budgetPeriodReplicationWriter.replicateOne(futureId, currentYearMonth, futureYm, periods)
            }.fold(
                onSuccess = { updated.add(futureId) },
                onFailure = { failed.add(FailedReplication(yearMonth = future.yearMonth, reason = it.message ?: it::class.simpleName ?: "unknown")) },
            )
        }

        BudgetPeriodReplicationResult(updated = updated, failed = failed)
    }
}
