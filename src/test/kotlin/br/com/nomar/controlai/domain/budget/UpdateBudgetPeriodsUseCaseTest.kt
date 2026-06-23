package br.com.nomar.controlai.domain.budget

import br.com.nomar.controlai.domain.budget.entity.BudgetPaymentPeriod
import br.com.nomar.controlai.domain.budget.entity.BudgetPeriodReplicationResult
import br.com.nomar.controlai.domain.budget.entity.FailedReplication
import br.com.nomar.controlai.domain.budget.gateway.ReplicateBudgetPeriodsToFutureGateway
import br.com.nomar.controlai.domain.budget.gateway.UpdateBudgetPeriodsGateway
import br.com.nomar.controlai.domain.budget.usecase.UpdateBudgetPeriodsUseCase
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UpdateBudgetPeriodsUseCaseTest {

    private val noopReplication = ReplicateBudgetPeriodsToFutureGateway { _, _ ->
        Result.success(BudgetPeriodReplicationResult(updated = emptyList(), failed = emptyList()))
    }

    private fun samplePeriods() = listOf(
        BudgetPaymentPeriod(
            budgetId = 1,
            paymentMethodId = 10,
            startDate = LocalDate.of(2026, 4, 10),
            endDate = LocalDate.of(2026, 5, 9),
        )
    )

    @Test
    fun `should not invoke replication when flag is false`() {
        var replicationInvoked = false
        val replicationGateway = ReplicateBudgetPeriodsToFutureGateway { _, _ ->
            replicationInvoked = true
            Result.success(BudgetPeriodReplicationResult(emptyList(), emptyList()))
        }
        val useCase = UpdateBudgetPeriodsUseCase(
            updateBudgetPeriodsGateway = { _, _ -> Result.success(Unit) },
            replicateBudgetPeriodsToFutureGateway = replicationGateway,
            meterRegistry = SimpleMeterRegistry(),
        )

        val result = useCase.execute(1L, samplePeriods(), replicateToFuture = false)

        assertTrue(result.isSuccess)
        assertTrue(result.getOrNull()!!.updated.isEmpty())
        assertTrue(result.getOrNull()!!.failed.isEmpty())
        assertEquals(false, replicationInvoked)
    }

    @Test
    fun `should propagate failure when current month update fails and skip replication`() {
        var replicationInvoked = false
        val replicationGateway = ReplicateBudgetPeriodsToFutureGateway { _, _ ->
            replicationInvoked = true
            Result.success(BudgetPeriodReplicationResult(emptyList(), emptyList()))
        }
        val useCase = UpdateBudgetPeriodsUseCase(
            updateBudgetPeriodsGateway = { _, _ -> Result.failure(IllegalStateException("db error")) },
            replicateBudgetPeriodsToFutureGateway = replicationGateway,
            meterRegistry = SimpleMeterRegistry(),
        )

        val result = useCase.execute(1L, samplePeriods(), replicateToFuture = true)

        assertTrue(result.isFailure)
        assertEquals(false, replicationInvoked)
    }

    @Test
    fun `should record ok metric when replication has no failures`() {
        val registry = SimpleMeterRegistry()
        val useCase = UpdateBudgetPeriodsUseCase(
            updateBudgetPeriodsGateway = { _, _ -> Result.success(Unit) },
            replicateBudgetPeriodsToFutureGateway = ReplicateBudgetPeriodsToFutureGateway { _, _ ->
                Result.success(BudgetPeriodReplicationResult(updated = listOf(2L, 3L), failed = emptyList()))
            },
            meterRegistry = registry,
        )

        val result = useCase.execute(1L, samplePeriods(), replicateToFuture = true)

        assertTrue(result.isSuccess)
        assertEquals(listOf(2L, 3L), result.getOrNull()!!.updated)
        assertEquals(1.0, registry.counter("budget_periods_replicated_total", "result", "ok").count())
    }

    @Test
    fun `should record partial metric when some replication entries fail`() {
        val registry = SimpleMeterRegistry()
        val useCase = UpdateBudgetPeriodsUseCase(
            updateBudgetPeriodsGateway = { _, _ -> Result.success(Unit) },
            replicateBudgetPeriodsToFutureGateway = ReplicateBudgetPeriodsToFutureGateway { _, _ ->
                Result.success(BudgetPeriodReplicationResult(
                    updated = listOf(2L),
                    failed = listOf(FailedReplication(yearMonth = "2026-07", reason = "db error")),
                ))
            },
            meterRegistry = registry,
        )

        val result = useCase.execute(1L, samplePeriods(), replicateToFuture = true)

        assertTrue(result.isSuccess)
        val payload = result.getOrNull()!!
        assertEquals(listOf(2L), payload.updated)
        assertEquals(1, payload.failed.size)
        assertEquals("2026-07", payload.failed.first().yearMonth)
        assertEquals(1.0, registry.counter("budget_periods_replicated_total", "result", "partial").count())
    }

    @Test
    fun `should record failed metric when all replication entries fail`() {
        val registry = SimpleMeterRegistry()
        val useCase = UpdateBudgetPeriodsUseCase(
            updateBudgetPeriodsGateway = { _, _ -> Result.success(Unit) },
            replicateBudgetPeriodsToFutureGateway = ReplicateBudgetPeriodsToFutureGateway { _, _ ->
                Result.success(BudgetPeriodReplicationResult(
                    updated = emptyList(),
                    failed = listOf(
                        FailedReplication(yearMonth = "2026-07", reason = "db error"),
                        FailedReplication(yearMonth = "2026-08", reason = "db error"),
                    ),
                ))
            },
            meterRegistry = registry,
        )

        val result = useCase.execute(1L, samplePeriods(), replicateToFuture = true)

        assertTrue(result.isSuccess)
        assertEquals(2, result.getOrNull()!!.failed.size)
        assertEquals(1.0, registry.counter("budget_periods_replicated_total", "result", "failed").count())
    }

    @Test
    fun `should still report current month success when replication gateway throws`() {
        val useCase = UpdateBudgetPeriodsUseCase(
            updateBudgetPeriodsGateway = { _, _ -> Result.success(Unit) },
            replicateBudgetPeriodsToFutureGateway = ReplicateBudgetPeriodsToFutureGateway { _, _ ->
                Result.failure(IllegalStateException("replication blew up"))
            },
            meterRegistry = SimpleMeterRegistry(),
        )

        // Per RF4.6, replication failure as a whole should not revert the current month.
        // The use case maps gateway exception to Result.failure so the controller can surface
        // a 500, but the current month is already persisted by the time we get here.
        val result = useCase.execute(1L, samplePeriods(), replicateToFuture = true)

        assertTrue(result.isFailure)
        assertEquals("replication blew up", result.exceptionOrNull()?.message)
    }
}
