package br.com.nomar.controlai.domain.auth

import br.com.nomar.controlai.domain.auth.entity.AuthSession
import br.com.nomar.controlai.domain.auth.entity.RefreshToken
import br.com.nomar.controlai.domain.auth.entity.User
import br.com.nomar.controlai.domain.auth.exception.InvalidRefreshTokenException
import br.com.nomar.controlai.domain.auth.usecase.RefreshSessionUseCase
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RefreshSessionUseCaseTest {

    private val user = User(id = 1, name = "Ramon", email = "ramon@email.com", passwordHash = "hash")

    private fun storedToken(
        rawToken: String,
        revokedAt: Instant? = null,
        replacedById: Long? = null,
        expiresAt: Instant = Instant.now().plus(30, ChronoUnit.DAYS),
        absoluteExpiresAt: Instant = Instant.now().plus(90, ChronoUnit.DAYS),
    ) = RefreshToken(
        id = 10,
        userId = 1,
        tokenHash = TokenHasher.sha256(rawToken),
        expiresAt = expiresAt,
        absoluteExpiresAt = absoluteExpiresAt,
        revokedAt = revokedAt,
        replacedById = replacedById,
    )

    @Test
    fun `should rotate a valid refresh token and return a new session`() {
        var rotated = false
        val useCase = RefreshSessionUseCase(
            findRefreshTokenGateway = { Result.success(storedToken("valid-token")) },
            findUserByIdGateway = { Result.success(user) },
            rotateRefreshTokenGateway = { _, u ->
                rotated = true
                Result.success(AuthSession("new-access", "new-refresh", u))
            },
            revokeTokenFamilyGateway = { Result.success(0) },
            meterRegistry = SimpleMeterRegistry(),
        )

        val result = useCase.execute("valid-token")

        assertTrue(result.isSuccess)
        assertTrue(rotated)
        assertEquals("new-refresh", result.getOrNull()?.refreshToken)
    }

    @Test
    fun `should revoke the whole family and count reuse when a rotated token is presented again`() {
        val meterRegistry = SimpleMeterRegistry()
        var familyRevoked = false
        var rotated = false
        val useCase = RefreshSessionUseCase(
            findRefreshTokenGateway = { Result.success(storedToken("stolen-token", replacedById = 11)) },
            findUserByIdGateway = { Result.success(user) },
            rotateRefreshTokenGateway = { _, u ->
                rotated = true
                Result.success(AuthSession("new-access", "new-refresh", u))
            },
            revokeTokenFamilyGateway = {
                familyRevoked = true
                Result.success(2)
            },
            meterRegistry = meterRegistry,
        )

        val result = useCase.execute("stolen-token")

        assertTrue(result.isFailure)
        assertIs<InvalidRefreshTokenException>(result.exceptionOrNull())
        assertTrue(familyRevoked)
        assertFalse(rotated)
        assertEquals(1.0, meterRegistry.counter("auth.refresh.reuse").count())
    }

    @Test
    fun `should fail without rotating when the token is expired`() {
        var rotated = false
        val useCase = RefreshSessionUseCase(
            findRefreshTokenGateway = {
                Result.success(storedToken("expired-token", expiresAt = Instant.now().minusSeconds(60)))
            },
            findUserByIdGateway = { Result.success(user) },
            rotateRefreshTokenGateway = { _, u ->
                rotated = true
                Result.success(AuthSession("new-access", "new-refresh", u))
            },
            revokeTokenFamilyGateway = { Result.success(0) },
            meterRegistry = SimpleMeterRegistry(),
        )

        val result = useCase.execute("expired-token")

        assertTrue(result.isFailure)
        assertIs<InvalidRefreshTokenException>(result.exceptionOrNull())
        assertFalse(rotated)
    }

    @Test
    fun `should fail when the absolute expiration has passed even if sliding window is valid`() {
        val useCase = RefreshSessionUseCase(
            findRefreshTokenGateway = {
                Result.success(
                    storedToken(
                        "old-family-token",
                        expiresAt = Instant.now().plus(1, ChronoUnit.DAYS),
                        absoluteExpiresAt = Instant.now().minusSeconds(60),
                    ),
                )
            },
            findUserByIdGateway = { Result.success(user) },
            rotateRefreshTokenGateway = { _, u -> Result.success(AuthSession("a", "r", u)) },
            revokeTokenFamilyGateway = { Result.success(0) },
            meterRegistry = SimpleMeterRegistry(),
        )

        assertTrue(useCase.execute("old-family-token").isFailure)
    }

    @Test
    fun `should fail when the token is unknown`() {
        val useCase = RefreshSessionUseCase(
            findRefreshTokenGateway = { Result.success(null) },
            findUserByIdGateway = { Result.success(user) },
            rotateRefreshTokenGateway = { _, u -> Result.success(AuthSession("a", "r", u)) },
            revokeTokenFamilyGateway = { Result.success(0) },
            meterRegistry = SimpleMeterRegistry(),
        )

        val result = useCase.execute("unknown-token")

        assertTrue(result.isFailure)
        assertIs<InvalidRefreshTokenException>(result.exceptionOrNull())
    }
}
