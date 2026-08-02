package br.com.nomar.controlai.domain.auth

import br.com.nomar.controlai.domain.auth.entity.PasswordResetToken
import br.com.nomar.controlai.domain.auth.entity.User
import br.com.nomar.controlai.domain.auth.exception.InvalidCredentialsException
import br.com.nomar.controlai.domain.auth.exception.InvalidResetTokenException
import br.com.nomar.controlai.domain.auth.gateway.CreatePasswordResetTokenGateway
import br.com.nomar.controlai.domain.auth.gateway.EmailGateway
import br.com.nomar.controlai.domain.auth.gateway.FindPasswordResetTokenByHashGateway
import br.com.nomar.controlai.domain.auth.gateway.FindUserByEmailGateway
import br.com.nomar.controlai.domain.auth.gateway.FindUserByIdGateway
import br.com.nomar.controlai.domain.auth.gateway.MarkPasswordResetTokenUsedGateway
import br.com.nomar.controlai.domain.auth.gateway.PasswordHashGateway
import br.com.nomar.controlai.domain.auth.gateway.RevokeAllRefreshTokensByUserIdGateway
import br.com.nomar.controlai.domain.auth.gateway.UpdateUserPasswordGateway
import br.com.nomar.controlai.domain.auth.usecase.ChangePasswordUseCase
import br.com.nomar.controlai.domain.auth.usecase.ForgotPasswordUseCase
import br.com.nomar.controlai.domain.auth.usecase.ResetPasswordUseCase
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PasswordUseCasesTest {

    private class FakePasswordHash : PasswordHashGateway {
        override fun hash(rawPassword: String) = "hashed:$rawPassword"
        override fun matches(rawPassword: String, passwordHash: String) = passwordHash == "hashed:$rawPassword"
    }

    private val fakePasswordHash = FakePasswordHash()

    private fun userWithPassword(id: Long = 1L) =
        User(id = id, name = "Ramon", email = "ramon@test.com", passwordHash = "hashed:secret123")

    private fun validToken(userId: Long = 1L, rawToken: String = "raw-token") = PasswordResetToken(
        id = 1L,
        userId = userId,
        tokenHash = TokenHasher.sha256(rawToken),
        expiresAt = Instant.now().plus(1, ChronoUnit.HOURS),
    )

    // ---- ForgotPasswordUseCase ----

    private fun forgotUseCase(
        user: User? = userWithPassword(),
        emailResult: Result<Unit> = Result.success(Unit),
        capturedLinks: MutableList<String> = mutableListOf(),
    ): ForgotPasswordUseCase {
        val emailGateway = object : EmailGateway {
            override fun sendPasswordReset(toEmail: String, toName: String, resetLink: String): Result<Unit> {
                capturedLinks.add(resetLink)
                return emailResult
            }
            override fun sendGroupInvite(toEmail: String, inviteLink: String) = Result.success(Unit)
        }
        return ForgotPasswordUseCase(
            findUserByEmailGateway = FindUserByEmailGateway { Result.success(user) },
            createPasswordResetTokenGateway = CreatePasswordResetTokenGateway { Result.success(it) },
            emailGateway = emailGateway,
            appWebUrl = "http://localhost:3000",
        )
    }

    @Test
    fun `ForgotPasswordUseCase should always succeed even for unknown email`() {
        val result = forgotUseCase(user = null).execute("ghost@test.com")
        assertTrue(result.isSuccess)
    }

    @Test
    fun `ForgotPasswordUseCase should send email with reset link for existing user`() {
        val capturedLinks = mutableListOf<String>()
        val result = forgotUseCase(capturedLinks = capturedLinks).execute("ramon@test.com")
        assertTrue(result.isSuccess)
        assertEquals(1, capturedLinks.size)
        assertTrue(capturedLinks[0].startsWith("http://localhost:3000/reset-password?token="))
    }

    @Test
    fun `ForgotPasswordUseCase should succeed even if email sending fails`() {
        val result = forgotUseCase(emailResult = Result.failure(RuntimeException("Resend down")))
            .execute("ramon@test.com")
        assertTrue(result.isSuccess)
    }

    @Test
    fun `ForgotPasswordUseCase should not send email for unknown user`() {
        val capturedLinks = mutableListOf<String>()
        forgotUseCase(user = null, capturedLinks = capturedLinks).execute("nobody@test.com")
        assertTrue(capturedLinks.isEmpty())
    }

    // ---- ResetPasswordUseCase ----

    private fun resetUseCase(
        token: PasswordResetToken?,
        updatedUserId: MutableList<Long> = mutableListOf(),
        revokedUserId: MutableList<Long> = mutableListOf(),
    ): ResetPasswordUseCase = ResetPasswordUseCase(
        findPasswordResetTokenByHashGateway = FindPasswordResetTokenByHashGateway {
            Result.success(token)
        },
        markPasswordResetTokenUsedGateway = MarkPasswordResetTokenUsedGateway { Result.success(Unit) },
        passwordHashGateway = fakePasswordHash,
        updateUserPasswordGateway = UpdateUserPasswordGateway { userId, _ ->
            updatedUserId.add(userId)
            Result.success(Unit)
        },
        revokeAllRefreshTokensByUserIdGateway = RevokeAllRefreshTokensByUserIdGateway { userId ->
            revokedUserId.add(userId)
            Result.success(Unit)
        },
    )

    @Test
    fun `ResetPasswordUseCase should update password and revoke sessions on valid token`() {
        val updated = mutableListOf<Long>()
        val revoked = mutableListOf<Long>()
        val result = resetUseCase(validToken(), updatedUserId = updated, revokedUserId = revoked)
            .execute("raw-token", "newpassword123")
        assertTrue(result.isSuccess)
        assertEquals(listOf(1L), updated)
        assertEquals(listOf(1L), revoked)
    }

    @Test
    fun `ResetPasswordUseCase should fail for unknown token`() {
        val result = resetUseCase(token = null).execute("bad-token", "newpassword123")
        assertTrue(result.isFailure)
        assertIs<InvalidResetTokenException>(result.exceptionOrNull())
    }

    @Test
    fun `ResetPasswordUseCase should fail for expired token`() {
        val expired = PasswordResetToken(
            id = 1L,
            userId = 1L,
            tokenHash = TokenHasher.sha256("raw-token"),
            expiresAt = Instant.now().minus(1, ChronoUnit.HOURS),
        )
        val result = resetUseCase(token = expired).execute("raw-token", "newpassword123")
        assertTrue(result.isFailure)
        assertIs<InvalidResetTokenException>(result.exceptionOrNull())
    }

    @Test
    fun `ResetPasswordUseCase should fail for already-used token`() {
        val used = PasswordResetToken(
            id = 1L,
            userId = 1L,
            tokenHash = TokenHasher.sha256("raw-token"),
            expiresAt = Instant.now().plus(1, ChronoUnit.HOURS),
            usedAt = Instant.now().minus(5, ChronoUnit.MINUTES),
        )
        val result = resetUseCase(token = used).execute("raw-token", "newpassword123")
        assertTrue(result.isFailure)
        assertIs<InvalidResetTokenException>(result.exceptionOrNull())
    }

    @Test
    fun `ResetPasswordUseCase should fail when new password is shorter than 8 chars`() {
        val result = resetUseCase(validToken()).execute("raw-token", "1234567")
        assertTrue(result.isFailure)
        assertIs<IllegalArgumentException>(result.exceptionOrNull())
    }

    // ---- ChangePasswordUseCase ----

    private fun changeUseCase(
        user: User?,
        updatedUserId: MutableList<Long> = mutableListOf(),
    ): ChangePasswordUseCase = ChangePasswordUseCase(
        findUserByIdGateway = FindUserByIdGateway { Result.success(user) },
        passwordHashGateway = fakePasswordHash,
        updateUserPasswordGateway = UpdateUserPasswordGateway { userId, _ ->
            updatedUserId.add(userId)
            Result.success(Unit)
        },
    )

    @Test
    fun `ChangePasswordUseCase should update password when current password is correct`() {
        val updated = mutableListOf<Long>()
        val result = changeUseCase(userWithPassword(), updatedUserId = updated)
            .execute(1L, "secret123", "newpassword123")
        assertTrue(result.isSuccess)
        assertEquals(listOf(1L), updated)
    }

    @Test
    fun `ChangePasswordUseCase should fail when current password is wrong`() {
        val result = changeUseCase(userWithPassword()).execute(1L, "wrong-pass", "newpassword123")
        assertTrue(result.isFailure)
        assertIs<InvalidCredentialsException>(result.exceptionOrNull())
    }

    @Test
    fun `ChangePasswordUseCase should fail for google-only account without password`() {
        val googleUser = User(id = 1L, name = "Google User", email = "g@test.com", passwordHash = null)
        val result = changeUseCase(googleUser).execute(1L, "anypassword", "newpassword123")
        assertTrue(result.isFailure)
        assertIs<InvalidCredentialsException>(result.exceptionOrNull())
    }

    @Test
    fun `ChangePasswordUseCase should fail when new password is shorter than 8 chars`() {
        var updateCalled = false
        val useCase = ChangePasswordUseCase(
            findUserByIdGateway = FindUserByIdGateway { Result.success(userWithPassword()) },
            passwordHashGateway = fakePasswordHash,
            updateUserPasswordGateway = UpdateUserPasswordGateway { _, _ ->
                updateCalled = true
                Result.success(Unit)
            },
        )
        val result = useCase.execute(1L, "secret123", "1234567")
        assertTrue(result.isFailure)
        assertIs<IllegalArgumentException>(result.exceptionOrNull())
        assertEquals(false, updateCalled)
    }

    @Test
    fun `ChangePasswordUseCase should use same error for wrong password and google-only account`() {
        val wrongPassResult = changeUseCase(userWithPassword()).execute(1L, "wrong", "newpassword123")
        val googleUser = User(id = 1L, name = "G", email = "g@test.com", passwordHash = null)
        val googleOnlyResult = changeUseCase(googleUser).execute(1L, "anypass", "newpassword123")

        assertIs<InvalidCredentialsException>(wrongPassResult.exceptionOrNull())
        assertIs<InvalidCredentialsException>(googleOnlyResult.exceptionOrNull())
        assertEquals(wrongPassResult.exceptionOrNull()?.message, googleOnlyResult.exceptionOrNull()?.message)
    }

    // ---- PasswordResetToken entity helpers ----

    @Test
    fun `PasswordResetToken isValid should be true when not expired and not used`() {
        val token = validToken()
        assertTrue(token.isValid())
    }

    @Test
    fun `PasswordResetToken isValid should be false when expired`() {
        val token = PasswordResetToken(
            id = 1L,
            userId = 1L,
            tokenHash = "hash",
            expiresAt = Instant.now().minus(1, ChronoUnit.MINUTES),
        )
        assertTrue(token.isExpired())
        assertTrue(!token.isValid())
    }

    @Test
    fun `PasswordResetToken isValid should be false when used`() {
        val token = PasswordResetToken(
            id = 1L,
            userId = 1L,
            tokenHash = "hash",
            expiresAt = Instant.now().plus(1, ChronoUnit.HOURS),
            usedAt = Instant.now(),
        )
        assertTrue(token.isUsed())
        assertTrue(!token.isValid())
    }
}
