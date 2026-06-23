package br.com.nomar.controlai.domain.budget.usecase

import br.com.nomar.controlai.domain.budget.entity.BudgetPaymentPeriod
import br.com.nomar.controlai.domain.budget.entity.BudgetPeriodReplicationResult
import br.com.nomar.controlai.domain.budget.gateway.ReplicateBudgetPeriodsToFutureGateway
import br.com.nomar.controlai.domain.budget.gateway.UpdateBudgetPeriodsGateway
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Component

@Component
class UpdateBudgetPeriodsUseCase(
    private val updateBudgetPeriodsGateway: UpdateBudgetPeriodsGateway,
    private val replicateBudgetPeriodsToFutureGateway: ReplicateBudgetPeriodsToFutureGateway,
    private val meterRegistry: MeterRegistry,
) {
    fun execute(
        budgetId: Long,
        periods: List<BudgetPaymentPeriod>,
        replicateToFuture: Boolean = false,
    ): Result<BudgetPeriodReplicationResult> {
        return runCatching {
            updateBudgetPeriodsGateway.execute(budgetId, periods).getOrThrow()

            if (!replicateToFuture) {
                return@runCatching BudgetPeriodReplicationResult(updated = emptyList(), failed = emptyList())
            }

            val replication = replicateBudgetPeriodsToFutureGateway.execute(budgetId, periods).getOrThrow()
            recordReplicationMetric(replication)
            replication
        }
    }

    private fun recordReplicationMetric(replication: BudgetPeriodReplicationResult) {
        val result = when {
            replication.failed.isEmpty() -> "ok"
            replication.updated.isEmpty() -> "failed"
            else -> "partial"
        }
        meterRegistry.counter("budget_periods_replicated_total", "result", result).increment()
    }
}
