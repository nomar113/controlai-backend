package br.com.nomar.controlai.domain.account_deletion

import br.com.nomar.controlai.domain.account_deletion.gateway.FindDeletionScheduleByUserIdGateway
import br.com.nomar.controlai.domain.account_deletion.gateway.FindDeletionSchedulesByGroupIdGateway
import br.com.nomar.controlai.domain.account_deletion.usecase.ResolveGroupDeletionBlockUseCase
import br.com.nomar.controlai.domain.account_deletion.usecase.ResolveUserDeletionBlockUseCase
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ResolveDeletionBlockUseCasesTest {

    private val userDate = Instant.parse("2026-10-23T15:00:00Z")
    private val partnerDate = Instant.parse("2026-10-25T09:30:00Z")

    private fun userUseCase(schedule: Result<Instant?>) =
        ResolveUserDeletionBlockUseCase(FindDeletionScheduleByUserIdGateway { schedule })

    private fun groupUseCase(schedules: Result<List<Instant?>>) =
        ResolveGroupDeletionBlockUseCase(FindDeletionSchedulesByGroupIdGateway { schedules })

    // ---- JWT: the logged-in user ----

    @Test
    fun `user with a scheduled deletion is blocked until that date`() {
        assertEquals(userDate, userUseCase(Result.success(userDate)).execute(1L).getOrThrow())
    }

    @Test
    fun `user without a scheduled deletion is not blocked`() {
        assertNull(userUseCase(Result.success(null)).execute(1L).getOrThrow())
    }

    @Test
    fun `user lookup failure is propagated`() {
        val result = userUseCase(Result.failure(IllegalStateException("db down"))).execute(1L)

        assertTrue(result.isFailure)
    }

    // ---- API key: the whole group ----

    @Test
    fun `group whose sole member asked for deletion is blocked`() {
        assertEquals(userDate, groupUseCase(Result.success(listOf(userDate))).execute(1L).getOrThrow())
    }

    @Test
    fun `group with an active partner is not blocked`() {
        assertNull(groupUseCase(Result.success(listOf(userDate, null))).execute(1L).getOrThrow())
    }

    @Test
    fun `group whose partner also asked for deletion is blocked until the latest date`() {
        val result = groupUseCase(Result.success(listOf(partnerDate, userDate))).execute(1L)

        assertEquals(partnerDate, result.getOrThrow())
    }

    @Test
    fun `group without any member deletion is not blocked`() {
        assertNull(groupUseCase(Result.success(listOf(null, null))).execute(1L).getOrThrow())
    }

    @Test
    fun `group without members is not blocked`() {
        assertNull(groupUseCase(Result.success(emptyList())).execute(1L).getOrThrow())
    }
}
