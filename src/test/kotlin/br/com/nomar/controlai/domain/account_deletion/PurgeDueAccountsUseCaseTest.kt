package br.com.nomar.controlai.domain.account_deletion

import br.com.nomar.controlai.domain.account_deletion.entity.PurgeOutcome
import br.com.nomar.controlai.domain.account_deletion.entity.PurgeSummary
import br.com.nomar.controlai.domain.account_deletion.gateway.FindDueAccountDeletionsGateway
import br.com.nomar.controlai.domain.account_deletion.gateway.PurgeAccountGateway
import br.com.nomar.controlai.domain.account_deletion.usecase.PurgeDueAccountsUseCase
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PurgeDueAccountsUseCaseTest {

    private val now = Instant.parse("2026-10-23T07:00:00Z")
    private val fixedClock = Clock.fixed(now, ZoneOffset.UTC)
    private val meterRegistry = SimpleMeterRegistry()

    // In-memory "users" table: user id -> deletion date. The fake gateway applies the same
    // rule as the real query (due at or before the given instant).
    private val schedules = mapOf(
        1L to now.minusSeconds(86_400),
        2L to now,
        3L to now.plusSeconds(1),
        4L to now.minusSeconds(3_600),
    )
    private val findDue = FindDueAccountDeletionsGateway { at ->
        Result.success(schedules.filterValues { !it.isAfter(at) }.keys.sorted())
    }

    private fun useCase(purge: PurgeAccountGateway) =
        PurgeDueAccountsUseCase(findDue, purge, meterRegistry, fixedClock)

    private fun purgedCount(outcome: String) = meterRegistry.counter("account.deletion.purged", "outcome", outcome).count()

    private fun failureCount() = meterRegistry.counter("account.deletion.purge.failure").count()

    @Test
    fun `purges every due account and ignores the ones not due yet`() {
        val purged = mutableListOf<Long>()

        val summary = useCase { userId ->
            purged.add(userId)
            Result.success(PurgeOutcome.MEMBER_REMOVED)
        }.execute().getOrThrow()

        assertEquals(listOf(1L, 2L, 4L), purged)
        assertEquals(PurgeSummary(due = 3, purged = 3, skipped = 0, failed = 0), summary)
        assertEquals(3.0, purgedCount("member_removed"))
    }

    @Test
    fun `a failing account does not stop the others and is counted as a failure`() {
        val attempted = mutableListOf<Long>()

        val summary = useCase { userId ->
            attempted.add(userId)
            if (userId == 1L) Result.failure(IllegalStateException("FK violation")) else Result.success(PurgeOutcome.SOLE_MEMBER_GROUP_PURGED)
        }.execute().getOrThrow()

        assertEquals(listOf(1L, 2L, 4L), attempted)
        assertEquals(PurgeSummary(due = 3, purged = 2, skipped = 0, failed = 1), summary)
        assertEquals(2.0, purgedCount("sole_member"))
        assertEquals(1.0, failureCount())
    }

    @Test
    fun `tags each purge with its outcome and does not count a cancelled deletion as purged`() {
        val outcomes = mapOf(
            1L to PurgeOutcome.SOLE_MEMBER_GROUP_PURGED,
            2L to PurgeOutcome.MEMBER_REMOVED,
            4L to PurgeOutcome.NO_LONGER_DUE,
        )

        val summary = useCase { userId -> Result.success(outcomes.getValue(userId)) }.execute().getOrThrow()

        assertEquals(PurgeSummary(due = 3, purged = 2, skipped = 1, failed = 0), summary)
        assertEquals(1.0, purgedCount("sole_member"))
        assertEquals(1.0, purgedCount("member_removed"))
        assertEquals(0.0, failureCount())
    }

    @Test
    fun `does nothing when no account is due`() {
        val useCase = PurgeDueAccountsUseCase(
            findDueAccountDeletionsGateway = { Result.success(emptyList()) },
            purgeAccountGateway = { error("must not be called") },
            meterRegistry = meterRegistry,
            clock = fixedClock,
        )

        assertEquals(PurgeSummary(due = 0, purged = 0, skipped = 0, failed = 0), useCase.execute().getOrThrow())
    }

    @Test
    fun `fails when the due accounts cannot be listed`() {
        val attempted = mutableListOf<Long>()
        val useCase = PurgeDueAccountsUseCase(
            findDueAccountDeletionsGateway = { Result.failure(IllegalStateException("db down")) },
            purgeAccountGateway = { userId ->
                attempted.add(userId)
                Result.success(PurgeOutcome.MEMBER_REMOVED)
            },
            meterRegistry = meterRegistry,
            clock = fixedClock,
        )

        assertIs<IllegalStateException>(useCase.execute().exceptionOrNull())
        assertTrue(attempted.isEmpty())
    }
}
