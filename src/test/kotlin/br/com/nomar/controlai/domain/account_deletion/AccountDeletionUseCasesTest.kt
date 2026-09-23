package br.com.nomar.controlai.domain.account_deletion

import br.com.nomar.controlai.domain.account_deletion.entity.AccountDeletion
import br.com.nomar.controlai.domain.account_deletion.exception.AccountDeletionAlreadyScheduledException
import br.com.nomar.controlai.domain.account_deletion.exception.AccountDeletionNotScheduledException
import br.com.nomar.controlai.domain.account_deletion.gateway.CancelAccountDeletionGateway
import br.com.nomar.controlai.domain.account_deletion.gateway.FindAccountDeletionGateway
import br.com.nomar.controlai.domain.account_deletion.gateway.ScheduleAccountDeletionGateway
import br.com.nomar.controlai.domain.account_deletion.usecase.CancelAccountDeletionUseCase
import br.com.nomar.controlai.domain.account_deletion.usecase.GetAccountDeletionUseCase
import br.com.nomar.controlai.domain.account_deletion.usecase.RequestAccountDeletionUseCase
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AccountDeletionUseCasesTest {

    private val now = Instant.parse("2026-09-23T15:00:00Z")
    private val fixedClock = Clock.fixed(now, ZoneOffset.UTC)
    private val scheduled = AccountDeletion(requestedAt = now, scheduledFor = Instant.parse("2026-10-23T15:00:00Z"))
    private val meterRegistry = SimpleMeterRegistry()

    // ---- RequestAccountDeletionUseCase ----

    private fun requestUseCase(
        existing: AccountDeletion? = null,
        saved: MutableList<Pair<Long, AccountDeletion>> = mutableListOf(),
    ) = RequestAccountDeletionUseCase(
        findAccountDeletionGateway = FindAccountDeletionGateway { Result.success(existing) },
        scheduleAccountDeletionGateway = ScheduleAccountDeletionGateway { userId, deletion ->
            saved.add(userId to deletion)
            Result.success(Unit)
        },
        meterRegistry = meterRegistry,
        clock = fixedClock,
    )

    @Test
    fun `RequestAccountDeletionUseCase schedules the deletion 30 days after now`() {
        val saved = mutableListOf<Pair<Long, AccountDeletion>>()

        val result = requestUseCase(saved = saved).execute(42L)

        val expected = AccountDeletion(requestedAt = now, scheduledFor = Instant.parse("2026-10-23T15:00:00Z"))
        assertEquals(expected, result.getOrThrow())
        assertEquals(listOf(42L to expected), saved)
        assertEquals(1.0, meterRegistry.counter("account.deletion.requested").count())
    }

    @Test
    fun `RequestAccountDeletionUseCase drops fractions of a second, as the database does`() {
        val useCase = RequestAccountDeletionUseCase(
            findAccountDeletionGateway = { Result.success(null) },
            scheduleAccountDeletionGateway = { _, _ -> Result.success(Unit) },
            meterRegistry = meterRegistry,
            clock = Clock.fixed(now.plusMillis(700), ZoneOffset.UTC),
        )

        val deletion = useCase.execute(42L).getOrThrow()

        assertEquals(now, deletion.requestedAt)
        assertEquals(Instant.parse("2026-10-23T15:00:00Z"), deletion.scheduledFor)
    }

    @Test
    fun `RequestAccountDeletionUseCase fails with conflict when a deletion is already scheduled`() {
        val saved = mutableListOf<Pair<Long, AccountDeletion>>()

        val result = requestUseCase(existing = scheduled, saved = saved).execute(42L)

        assertIs<AccountDeletionAlreadyScheduledException>(result.exceptionOrNull())
        assertTrue(saved.isEmpty())
        assertEquals(0.0, meterRegistry.counter("account.deletion.requested").count())
    }

    @Test
    fun `RequestAccountDeletionUseCase propagates a gateway failure`() {
        val useCase = RequestAccountDeletionUseCase(
            findAccountDeletionGateway = { Result.success(null) },
            scheduleAccountDeletionGateway = { _, _ -> Result.failure(IllegalStateException("db down")) },
            meterRegistry = meterRegistry,
            clock = fixedClock,
        )

        val result = useCase.execute(42L)

        assertIs<IllegalStateException>(result.exceptionOrNull())
        assertEquals(0.0, meterRegistry.counter("account.deletion.requested").count())
    }

    // ---- CancelAccountDeletionUseCase ----

    private fun cancelUseCase(existing: AccountDeletion?, cancelled: MutableList<Long> = mutableListOf()) =
        CancelAccountDeletionUseCase(
            findAccountDeletionGateway = FindAccountDeletionGateway { Result.success(existing) },
            cancelAccountDeletionGateway = CancelAccountDeletionGateway { userId ->
                cancelled.add(userId)
                Result.success(Unit)
            },
            meterRegistry = meterRegistry,
        )

    @Test
    fun `CancelAccountDeletionUseCase clears a scheduled deletion`() {
        val cancelled = mutableListOf<Long>()

        val result = cancelUseCase(existing = scheduled, cancelled = cancelled).execute(42L)

        assertTrue(result.isSuccess)
        assertEquals(listOf(42L), cancelled)
        assertEquals(1.0, meterRegistry.counter("account.deletion.cancelled").count())
    }

    @Test
    fun `CancelAccountDeletionUseCase fails with not found when nothing is scheduled`() {
        val cancelled = mutableListOf<Long>()

        val result = cancelUseCase(existing = null, cancelled = cancelled).execute(42L)

        assertIs<AccountDeletionNotScheduledException>(result.exceptionOrNull())
        assertTrue(cancelled.isEmpty())
        assertEquals(0.0, meterRegistry.counter("account.deletion.cancelled").count())
    }

    // ---- GetAccountDeletionUseCase ----

    @Test
    fun `GetAccountDeletionUseCase returns null when nothing is scheduled`() {
        val result = GetAccountDeletionUseCase { Result.success(null) }.execute(42L)

        assertNull(result.getOrThrow())
    }

    @Test
    fun `GetAccountDeletionUseCase returns the scheduled deletion`() {
        val result = GetAccountDeletionUseCase { Result.success(scheduled) }.execute(42L)

        assertEquals(scheduled, result.getOrThrow())
    }
}
