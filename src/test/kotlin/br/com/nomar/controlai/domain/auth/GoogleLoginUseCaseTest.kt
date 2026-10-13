package br.com.nomar.controlai.domain.auth

import br.com.nomar.controlai.domain.auth.entity.AuthSession
import br.com.nomar.controlai.domain.auth.entity.GoogleUserInfo
import br.com.nomar.controlai.domain.auth.entity.IdentityProvider
import br.com.nomar.controlai.domain.auth.entity.User
import br.com.nomar.controlai.domain.auth.entity.UserIdentity
import br.com.nomar.controlai.domain.auth.exception.InvalidGoogleTokenException
import br.com.nomar.controlai.domain.auth.usecase.GoogleLoginUseCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class GoogleLoginUseCaseTest {

    private val fakeGoogleUser = GoogleUserInfo(
        sub = "google-sub-123",
        email = "user@gmail.com",
        name = "Test User",
    )

    private val existingUser = User(id = 42, name = "Test User", email = "user@gmail.com", passwordHash = "hashed")

    private fun sessionFor(user: User) = AuthSession("access-token", "refresh-token", user)

    private fun useCase(
        verifyResult: Result<GoogleUserInfo> = Result.success(fakeGoogleUser),
        byIdentity: User? = null,
        byEmail: User? = null,
        createdUser: User = existingUser,
        onLink: (UserIdentity) -> Unit = {},
    ) = GoogleLoginUseCase(
        googleTokenVerifierGateway = { Result.success(verifyResult.getOrThrow()) },
        findUserByIdentityGateway = { provider, subject ->
            assertEquals(IdentityProvider.GOOGLE, provider)
            assertEquals("google-sub-123", subject)
            Result.success(byIdentity)
        },
        findUserByEmailGateway = { Result.success(byEmail) },
        linkIdentityGateway = { identity ->
            onLink(identity)
            Result.success(Unit)
        },
        createUserWithIdentityGateway = { _, _, _ -> Result.success(createdUser) },
        issueTokensGateway = { user -> Result.success(sessionFor(user)) },
    )

    // --- Path 1: user already has a GOOGLE identity ---

    @Test
    fun `should return session immediately when google identity is already linked`() {
        val linkedUser = User(id = 10, name = "Linked", email = "user@gmail.com")
        var linked = false
        val uc = useCase(byIdentity = linkedUser, onLink = { linked = true })

        val result = uc.execute("valid-id-token")

        assertTrue(result.isSuccess)
        assertEquals("access-token", result.getOrNull()?.accessToken)
        assertEquals(linkedUser, result.getOrNull()?.user)
        assertTrue(!linked, "must not link again when the identity already exists")
    }

    // --- Path 2: same email with password account (RF-1.5) ---

    @Test
    fun `should link google identity to existing email-password account (RF-1-5)`() {
        var linkedIdentity: UserIdentity? = null
        val uc = useCase(byEmail = existingUser, onLink = { linkedIdentity = it })

        val result = uc.execute("valid-id-token")

        assertTrue(result.isSuccess)
        assertEquals(existingUser, result.getOrNull()?.user)
        assertEquals(42L, linkedIdentity?.userId)
        assertEquals(IdentityProvider.GOOGLE, linkedIdentity?.provider)
        assertEquals("google-sub-123", linkedIdentity?.subject)
    }

    // --- Path 3: brand new account ---

    @Test
    fun `should create new google-only account with its identity and google_sub`() {
        var createdUser: User? = null
        var identityProvider: IdentityProvider? = null
        var identitySubject: String? = null
        val uc = GoogleLoginUseCase(
            googleTokenVerifierGateway = { Result.success(fakeGoogleUser) },
            findUserByIdentityGateway = { _, _ -> Result.success(null) },
            findUserByEmailGateway = { Result.success(null) },
            linkIdentityGateway = { error("should not link on the new-account path") },
            createUserWithIdentityGateway = { user, provider, subject ->
                createdUser = user
                identityProvider = provider
                identitySubject = subject
                Result.success(User(id = 99, name = user.name, email = user.email, googleSub = user.googleSub))
            },
            issueTokensGateway = { user -> Result.success(sessionFor(user)) },
        )

        val result = uc.execute("valid-id-token")

        assertTrue(result.isSuccess)
        assertEquals("user@gmail.com", createdUser?.email)
        assertEquals("google-sub-123", createdUser?.googleSub)
        assertEquals(null, createdUser?.passwordHash, "Google-only account must have no password")
        assertEquals(IdentityProvider.GOOGLE, identityProvider)
        assertEquals("google-sub-123", identitySubject)
    }

    @Test
    fun `should normalize email to lowercase when creating account`() {
        val mixedCaseGoogleUser = fakeGoogleUser.copy(email = "User@Gmail.COM")
        var createdUser: User? = null
        val uc = GoogleLoginUseCase(
            googleTokenVerifierGateway = { Result.success(mixedCaseGoogleUser) },
            findUserByIdentityGateway = { _, _ -> Result.success(null) },
            findUserByEmailGateway = { Result.success(null) },
            linkIdentityGateway = { Result.success(Unit) },
            createUserWithIdentityGateway = { user, _, _ ->
                createdUser = user
                Result.success(User(id = 1, name = user.name, email = user.email, googleSub = user.googleSub))
            },
            issueTokensGateway = { user -> Result.success(sessionFor(user)) },
        )

        uc.execute("valid-id-token")

        assertEquals("user@gmail.com", createdUser?.email)
    }

    // --- Invalid token ---

    @Test
    fun `should fail with InvalidGoogleTokenException when token is invalid`() {
        val uc = GoogleLoginUseCase(
            googleTokenVerifierGateway = { Result.failure(InvalidGoogleTokenException()) },
            findUserByIdentityGateway = { _, _ -> error("should not reach") },
            findUserByEmailGateway = { error("should not reach") },
            linkIdentityGateway = { error("should not reach") },
            createUserWithIdentityGateway = { _, _, _ -> error("should not reach") },
            issueTokensGateway = { error("should not reach") },
        )

        val result = uc.execute("bad-token")

        assertTrue(result.isFailure)
        assertIs<InvalidGoogleTokenException>(result.exceptionOrNull())
    }
}
