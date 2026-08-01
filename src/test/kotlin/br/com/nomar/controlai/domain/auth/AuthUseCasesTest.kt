package br.com.nomar.controlai.domain.auth

import br.com.nomar.controlai.domain.auth.entity.AuthSession
import br.com.nomar.controlai.domain.auth.entity.User
import br.com.nomar.controlai.domain.auth.exception.EmailAlreadyUsedException
import br.com.nomar.controlai.domain.auth.exception.InvalidCredentialsException
import br.com.nomar.controlai.domain.auth.gateway.CreateUserWithPersonalGroupGateway
import br.com.nomar.controlai.domain.auth.gateway.FindUserByEmailGateway
import br.com.nomar.controlai.domain.auth.gateway.IssueTokensGateway
import br.com.nomar.controlai.domain.auth.gateway.PasswordHashGateway
import br.com.nomar.controlai.domain.auth.gateway.RevokeRefreshTokenGateway
import br.com.nomar.controlai.domain.auth.usecase.LoginUseCase
import br.com.nomar.controlai.domain.auth.usecase.LogoutUseCase
import br.com.nomar.controlai.domain.auth.usecase.RegisterUserUseCase
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AuthUseCasesTest {

    private class FakePasswordHash : PasswordHashGateway {
        override fun hash(rawPassword: String) = "hashed:$rawPassword"
        override fun matches(rawPassword: String, passwordHash: String) = passwordHash == "hashed:$rawPassword"
    }

    private fun sessionFor(user: User) = AuthSession("access-token", "refresh-token", user)

    // --- RegisterUserUseCase ---

    @Test
    fun `RegisterUserUseCase should create user with personal group and hashed password`() {
        var createdUser: User? = null
        val useCase = RegisterUserUseCase(
            findUserByEmailGateway = { Result.success(null) },
            passwordHashGateway = FakePasswordHash(),
            createUserWithPersonalGroupGateway = { user ->
                createdUser = user
                Result.success(User(id = 1, name = user.name, email = user.email, passwordHash = user.passwordHash))
            },
            issueTokensGateway = { user -> Result.success(sessionFor(user)) },
        )

        val result = useCase.execute("Ramon", " Ramon@Email.com ", "secret123")

        assertTrue(result.isSuccess)
        assertEquals("ramon@email.com", createdUser?.email)
        assertEquals("hashed:secret123", createdUser?.passwordHash)
        assertEquals("access-token", result.getOrNull()?.accessToken)
    }

    @Test
    fun `RegisterUserUseCase should fail with EmailAlreadyUsedException when email exists`() {
        val useCase = RegisterUserUseCase(
            findUserByEmailGateway = { Result.success(User(id = 1, name = "X", email = it)) },
            passwordHashGateway = FakePasswordHash(),
            createUserWithPersonalGroupGateway = { Result.success(it) },
            issueTokensGateway = { Result.success(sessionFor(it)) },
        )

        val result = useCase.execute("Ramon", "ramon@email.com", "secret123")

        assertTrue(result.isFailure)
        assertIs<EmailAlreadyUsedException>(result.exceptionOrNull())
    }

    @Test
    fun `RegisterUserUseCase should reject password shorter than 8 characters without creating user`() {
        var createCalled = false
        val useCase = RegisterUserUseCase(
            findUserByEmailGateway = { Result.success(null) },
            passwordHashGateway = FakePasswordHash(),
            createUserWithPersonalGroupGateway = {
                createCalled = true
                Result.success(it)
            },
            issueTokensGateway = { Result.success(sessionFor(it)) },
        )

        val result = useCase.execute("Ramon", "ramon@email.com", "1234567")

        assertTrue(result.isFailure)
        assertIs<IllegalArgumentException>(result.exceptionOrNull())
        assertEquals(false, createCalled)
    }

    // --- LoginUseCase ---

    private fun loginUseCase(
        user: User?,
        meterRegistry: SimpleMeterRegistry = SimpleMeterRegistry(),
    ) = LoginUseCase(
        findUserByEmailGateway = { Result.success(user) },
        passwordHashGateway = FakePasswordHash(),
        issueTokensGateway = { Result.success(sessionFor(it)) },
        meterRegistry = meterRegistry,
    )

    @Test
    fun `LoginUseCase should return session and count success on valid credentials`() {
        val meterRegistry = SimpleMeterRegistry()
        val user = User(id = 1, name = "Ramon", email = "ramon@email.com", passwordHash = "hashed:secret123")

        val result = loginUseCase(user, meterRegistry).execute("ramon@email.com", "secret123")

        assertTrue(result.isSuccess)
        assertEquals("access-token", result.getOrNull()?.accessToken)
        assertEquals(1.0, meterRegistry.counter("auth.login.success").count())
    }

    @Test
    fun `LoginUseCase should fail with generic message on wrong password`() {
        val meterRegistry = SimpleMeterRegistry()
        val user = User(id = 1, name = "Ramon", email = "ramon@email.com", passwordHash = "hashed:secret123")

        val result = loginUseCase(user, meterRegistry).execute("ramon@email.com", "wrong-pass")

        assertTrue(result.isFailure)
        assertIs<InvalidCredentialsException>(result.exceptionOrNull())
        assertEquals("Credenciais invalidas", result.exceptionOrNull()?.message)
        assertEquals(1.0, meterRegistry.counter("auth.login.failure").count())
    }

    @Test
    fun `LoginUseCase should fail with the same generic message on unknown email`() {
        val unknownEmailResult = loginUseCase(user = null).execute("ghost@email.com", "secret123")
        val wrongPasswordResult = loginUseCase(
            User(id = 1, name = "Ramon", email = "ramon@email.com", passwordHash = "hashed:secret123"),
        ).execute("ramon@email.com", "wrong-pass")

        assertTrue(unknownEmailResult.isFailure)
        assertIs<InvalidCredentialsException>(unknownEmailResult.exceptionOrNull())
        // Same message in both cases so the API never reveals whether the email exists
        assertEquals(
            wrongPasswordResult.exceptionOrNull()?.message,
            unknownEmailResult.exceptionOrNull()?.message,
        )
    }

    @Test
    fun `LoginUseCase should fail for google-only account without password`() {
        val user = User(id = 1, name = "Ramon", email = "ramon@email.com", passwordHash = null)

        val result = loginUseCase(user).execute("ramon@email.com", "secret123")

        assertTrue(result.isFailure)
        assertIs<InvalidCredentialsException>(result.exceptionOrNull())
    }

    // --- LogoutUseCase ---

    @Test
    fun `LogoutUseCase should revoke the hashed refresh token`() {
        var revokedHash: String? = null
        val useCase = LogoutUseCase(
            revokeRefreshTokenGateway = { hash ->
                revokedHash = hash
                Result.success(Unit)
            },
        )

        val result = useCase.execute("raw-refresh-token")

        assertTrue(result.isSuccess)
        assertEquals(TokenHasher.sha256("raw-refresh-token"), revokedHash)
    }

    @Test
    fun `LogoutUseCase should propagate gateway failure`() {
        val useCase = LogoutUseCase(
            revokeRefreshTokenGateway = { Result.failure(IllegalStateException("db down")) },
        )

        val result = useCase.execute("raw-refresh-token")

        assertTrue(result.isFailure)
        assertNull(result.getOrNull())
    }
}
