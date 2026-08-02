package br.com.nomar.controlai.domain.auth

import br.com.nomar.controlai.domain.auth.entity.AuthSession
import br.com.nomar.controlai.domain.auth.entity.GoogleUserInfo
import br.com.nomar.controlai.domain.auth.entity.User
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
        byGoogleSub: User? = null,
        byEmail: User? = null,
        linkedUser: User = existingUser,
        createdUser: User = existingUser,
    ) = GoogleLoginUseCase(
        googleTokenVerifierGateway = { Result.success(verifyResult.getOrThrow()) },
        findUserByGoogleSubGateway = { Result.success(byGoogleSub) },
        findUserByEmailGateway = { Result.success(byEmail) },
        linkGoogleSubToUserGateway = { _, _ -> Result.success(linkedUser) },
        createUserWithPersonalGroupGateway = { Result.success(createdUser) },
        issueTokensGateway = { user -> Result.success(sessionFor(user)) },
    )

    // --- Path 1: user already has google_sub ---

    @Test
    fun `should return session immediately when google_sub is already linked`() {
        val linkedUser = User(id = 10, name = "Linked", email = "user@gmail.com", googleSub = "google-sub-123")
        val uc = useCase(byGoogleSub = linkedUser)

        val result = uc.execute("valid-id-token")

        assertTrue(result.isSuccess)
        assertEquals("access-token", result.getOrNull()?.accessToken)
        assertEquals(linkedUser, result.getOrNull()?.user)
    }

    // --- Path 2: same email with password account (RF-1.5) ---

    @Test
    fun `should link google_sub to existing email-password account (RF-1-5)`() {
        var linkedUserId: Long? = null
        var linkedSub: String? = null
        val uc = GoogleLoginUseCase(
            googleTokenVerifierGateway = { Result.success(fakeGoogleUser) },
            findUserByGoogleSubGateway = { Result.success(null) },
            findUserByEmailGateway = { Result.success(existingUser) },
            linkGoogleSubToUserGateway = { uid, sub ->
                linkedUserId = uid
                linkedSub = sub
                Result.success(User(id = existingUser.id, name = existingUser.name, email = existingUser.email, googleSub = sub))
            },
            createUserWithPersonalGroupGateway = { Result.success(it) },
            issueTokensGateway = { user -> Result.success(sessionFor(user)) },
        )

        val result = uc.execute("valid-id-token")

        assertTrue(result.isSuccess)
        assertEquals(42L, linkedUserId)
        assertEquals("google-sub-123", linkedSub)
    }

    // --- Path 3: brand new account ---

    @Test
    fun `should create new google-only account when no existing user found`() {
        var createdUser: User? = null
        val uc = GoogleLoginUseCase(
            googleTokenVerifierGateway = { Result.success(fakeGoogleUser) },
            findUserByGoogleSubGateway = { Result.success(null) },
            findUserByEmailGateway = { Result.success(null) },
            linkGoogleSubToUserGateway = { _, _ -> error("should not link") },
            createUserWithPersonalGroupGateway = { user ->
                createdUser = user
                Result.success(User(id = 99, name = user.name, email = user.email, googleSub = user.googleSub))
            },
            issueTokensGateway = { user -> Result.success(sessionFor(user)) },
        )

        val result = uc.execute("valid-id-token")

        assertTrue(result.isSuccess)
        assertEquals("user@gmail.com", createdUser?.email)
        assertEquals("google-sub-123", createdUser?.googleSub)
        assertEquals(null, createdUser?.passwordHash, "Google-only account must have no password")
    }

    @Test
    fun `should normalize email to lowercase when creating account`() {
        val mixedCaseGoogleUser = fakeGoogleUser.copy(email = "User@Gmail.COM")
        var createdUser: User? = null
        val uc = GoogleLoginUseCase(
            googleTokenVerifierGateway = { Result.success(mixedCaseGoogleUser) },
            findUserByGoogleSubGateway = { Result.success(null) },
            findUserByEmailGateway = { Result.success(null) },
            linkGoogleSubToUserGateway = { _, _ -> error("should not link") },
            createUserWithPersonalGroupGateway = { user ->
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
            findUserByGoogleSubGateway = { Result.success(null) },
            findUserByEmailGateway = { Result.success(null) },
            linkGoogleSubToUserGateway = { _, _ -> error("should not reach") },
            createUserWithPersonalGroupGateway = { error("should not reach") },
            issueTokensGateway = { error("should not reach") },
        )

        val result = uc.execute("bad-token")

        assertTrue(result.isFailure)
        assertIs<InvalidGoogleTokenException>(result.exceptionOrNull())
    }
}
