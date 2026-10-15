package br.com.nomar.controlai.domain.auth

import br.com.nomar.controlai.domain.auth.entity.AppleIdentity
import br.com.nomar.controlai.domain.auth.entity.AppleLoginCommand
import br.com.nomar.controlai.domain.auth.entity.AuthSession
import br.com.nomar.controlai.domain.auth.entity.IdentityProvider
import br.com.nomar.controlai.domain.auth.entity.User
import br.com.nomar.controlai.domain.auth.entity.UserIdentity
import br.com.nomar.controlai.domain.auth.exception.InvalidAppleTokenException
import br.com.nomar.controlai.domain.auth.gateway.AppleTokenExchangeGateway
import br.com.nomar.controlai.domain.auth.gateway.TokenCipherGateway
import br.com.nomar.controlai.domain.auth.usecase.AppleLoginUseCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppleLoginUseCaseTest {

    private val verifiedIdentity = AppleIdentity(
        subject = "apple-sub-001",
        email = "User@Example.com",
        emailVerified = true,
        isPrivateRelay = false,
        audience = "br.com.nomar.controlai",
    )

    private val relayIdentity = verifiedIdentity.copy(
        email = "abc123@privaterelay.appleid.com",
        isPrivateRelay = true,
    )

    private val existingUser = User(id = 42, name = "Existing", email = "user@example.com", passwordHash = "hashed")

    private val command = AppleLoginCommand(
        identityToken = "identity-token",
        authorizationCode = "auth-code",
        nonce = "raw-nonce",
        fullName = "Maria Souza",
    )

    // Records what the use case did, so each test can assert on side effects (or their absence)
    private class Calls {
        val exchanges = mutableListOf<Pair<String, String>>()
        val links = mutableListOf<UserIdentity>()
        val creations = mutableListOf<Triple<User, String, String?>>()
        val credentialUpdates = mutableListOf<Pair<Long, String?>>()
    }

    private fun useCase(
        verifyResult: Result<AppleIdentity> = Result.success(verifiedIdentity),
        byIdentity: User? = null,
        byEmail: User? = null,
        exchangeResult: Result<String> = Result.success("apple-refresh-token"),
        calls: Calls = Calls(),
    ) = AppleLoginUseCase(
        appleIdentityVerifierGateway = { token, nonce ->
            assertEquals("identity-token", token)
            assertEquals("raw-nonce", nonce)
            verifyResult
        },
        appleTokenExchangeGateway = object : AppleTokenExchangeGateway {
            override fun exchangeAuthorizationCode(authorizationCode: String, clientId: String): Result<String> {
                calls.exchanges += authorizationCode to clientId
                return exchangeResult
            }

            override fun revoke(refreshToken: String, clientId: String) = error("not used")
        },
        tokenCipherGateway = object : TokenCipherGateway {
            override fun encrypt(plainText: String) = "enc($plainText)"
            override fun decrypt(cipherText: String) = error("not used")
        },
        updateAppleRefreshCredentialGateway = { userId, encrypted ->
            calls.credentialUpdates += userId to encrypted
            Result.success(Unit)
        },
        findUserByIdentityGateway = { provider, subject ->
            assertEquals(IdentityProvider.APPLE, provider)
            assertEquals("apple-sub-001", subject)
            Result.success(byIdentity)
        },
        findUserByEmailGateway = { Result.success(byEmail) },
        linkIdentityGateway = { identity ->
            calls.links += identity
            Result.success(Unit)
        },
        createUserWithIdentityGateway = { user, identity ->
            calls.creations += Triple(user, identity.subject, identity.appleRefreshTokenEnc)
            assertEquals(IdentityProvider.APPLE, identity.provider)
            Result.success(User(id = 99, name = user.name, email = user.email))
        },
        issueTokensGateway = { user -> Result.success(AuthSession("access", "refresh", user)) },
    )

    @Test
    fun `login com identidade vinculada troca o codigo e substitui a credencial armazenada`() {
        val calls = Calls()

        val session = useCase(byIdentity = existingUser, calls = calls).execute(command).getOrThrow()

        assertEquals(42L, session.user.id)
        assertEquals(listOf("auth-code" to "br.com.nomar.controlai"), calls.exchanges)
        assertEquals(listOf<Pair<Long, String?>>(42L to "enc(br.com.nomar.controlai\napple-refresh-token)"), calls.credentialUpdates)
    }

    @Test
    fun `login com identidade vinculada entra mesmo quando a troca do codigo falha, sem gravar`() {
        val calls = Calls()

        val session = useCase(
            byIdentity = existingUser,
            exchangeResult = Result.failure(InvalidAppleTokenException()),
            calls = calls,
        ).execute(command).getOrThrow()

        assertEquals(42L, session.user.id)
        assertTrue(calls.credentialUpdates.isEmpty())
    }

    @Test
    fun `identidade Apple ja vinculada devolve a conta existente sem criar nem vincular outra`() {
        val calls = Calls()

        val session = useCase(byIdentity = existingUser, calls = calls).execute(command).getOrThrow()

        assertEquals(42L, session.user.id)
        assertTrue(calls.links.isEmpty())
        assertTrue(calls.creations.isEmpty())
    }

    @Test
    fun `e-mail verificado e nao relay igual a conta existente vincula a identidade`() {
        val calls = Calls()

        val session = useCase(byEmail = existingUser, calls = calls).execute(command).getOrThrow()

        assertEquals(42L, session.user.id)
        assertEquals(listOf("auth-code" to "br.com.nomar.controlai"), calls.exchanges)
        val link = calls.links.single()
        assertEquals(42L, link.userId)
        assertEquals(IdentityProvider.APPLE, link.provider)
        assertEquals("apple-sub-001", link.subject)
        assertEquals("enc(br.com.nomar.controlai\napple-refresh-token)", link.appleRefreshTokenEnc)
        assertTrue(calls.creations.isEmpty())
    }

    @Test
    fun `e-mail relay nunca vincula a conta existente e cria conta nova com o relay`() {
        val calls = Calls()

        val session = useCase(
            verifyResult = Result.success(relayIdentity),
            byEmail = null,
            calls = calls,
        ).execute(command).getOrThrow()

        assertEquals(99L, session.user.id)
        assertTrue(calls.links.isEmpty())
        val (user, _, _) = calls.creations.single()
        assertEquals("abc123@privaterelay.appleid.com", user.email)
    }

    @Test
    fun `relay com e-mail de conta existente falha sem vincular`() {
        val calls = Calls()
        val relayWithExistingEmail = relayIdentity.copy(email = "user@example.com")

        assertFailsWith<InvalidAppleTokenException> {
            useCase(
                verifyResult = Result.success(relayWithExistingEmail),
                byEmail = existingUser,
                calls = calls,
            ).execute(command).getOrThrow()
        }
        assertTrue(calls.links.isEmpty())
        assertTrue(calls.creations.isEmpty())
    }

    @Test
    fun `e-mail nao verificado de conta existente falha sem vincular nem duplicar`() {
        val calls = Calls()
        val unverified = verifiedIdentity.copy(emailVerified = false)

        assertFailsWith<InvalidAppleTokenException> {
            useCase(verifyResult = Result.success(unverified), byEmail = existingUser, calls = calls)
                .execute(command).getOrThrow()
        }
        assertTrue(calls.links.isEmpty())
        assertTrue(calls.creations.isEmpty())
    }

    @Test
    fun `conta nova usa o fullName do primeiro login e grava o refresh token cifrado`() {
        val calls = Calls()

        useCase(calls = calls).execute(command).getOrThrow()

        val (user, subject, encrypted) = calls.creations.single()
        assertEquals("Maria Souza", user.name)
        assertEquals("user@example.com", user.email)
        assertEquals("apple-sub-001", subject)
        assertEquals("enc(br.com.nomar.controlai\napple-refresh-token)", encrypted)
    }

    @Test
    fun `primeiro login sem nome usa a parte local do e-mail`() {
        val calls = Calls()

        useCase(calls = calls).execute(command.copy(fullName = null)).getOrThrow()

        assertEquals("user", calls.creations.single().first.name)
    }

    @Test
    fun `fullName em branco tambem usa a parte local do e-mail`() {
        val calls = Calls()

        useCase(calls = calls).execute(command.copy(fullName = "   ")).getOrThrow()

        assertEquals("user", calls.creations.single().first.name)
    }

    @Test
    fun `nonce divergente falha sem trocar codigo nem criar conta`() {
        val calls = Calls()
        val gateway = AppleLoginUseCase(
            appleIdentityVerifierGateway = { _, _ -> Result.failure(InvalidAppleTokenException()) },
            appleTokenExchangeGateway = object : AppleTokenExchangeGateway {
                override fun exchangeAuthorizationCode(authorizationCode: String, clientId: String): Result<String> {
                    calls.exchanges += authorizationCode to clientId
                    return Result.success("rt")
                }

                override fun revoke(refreshToken: String, clientId: String) = error("not used")
            },
            tokenCipherGateway = object : TokenCipherGateway {
                override fun encrypt(plainText: String) = plainText
                override fun decrypt(cipherText: String) = cipherText
            },
            updateAppleRefreshCredentialGateway = { _, _ -> Result.success(Unit) },
            findUserByIdentityGateway = { _, _ -> Result.success(null) },
            findUserByEmailGateway = { _ -> Result.success(null) },
            linkIdentityGateway = { identity -> calls.links += identity; Result.success(Unit) },
            createUserWithIdentityGateway = { user, _ -> calls.creations += Triple(user, "", null); Result.success(user) },
            issueTokensGateway = { user -> Result.success(AuthSession("a", "r", user)) },
        )

        val result = gateway.execute(command)

        assertIs<InvalidAppleTokenException>(result.exceptionOrNull())
        assertTrue(calls.exchanges.isEmpty())
        assertTrue(calls.creations.isEmpty())
        assertTrue(calls.links.isEmpty())
    }

    @Test
    fun `codigo de autorizacao invalido nao cria usuario`() {
        val calls = Calls()

        val result = useCase(
            exchangeResult = Result.failure(InvalidAppleTokenException()),
            calls = calls,
        ).execute(command)

        assertIs<InvalidAppleTokenException>(result.exceptionOrNull())
        assertEquals(1, calls.exchanges.size)
        assertTrue(calls.creations.isEmpty())
        assertTrue(calls.links.isEmpty())
    }

    @Test
    fun `codigo invalido ao vincular por e-mail tambem nao grava vinculo`() {
        val calls = Calls()

        val result = useCase(
            byEmail = existingUser,
            exchangeResult = Result.failure(InvalidAppleTokenException()),
            calls = calls,
        ).execute(command)

        assertIs<InvalidAppleTokenException>(result.exceptionOrNull())
        assertTrue(calls.links.isEmpty())
    }

    @Test
    fun `token sem e-mail falha sem criar conta`() {
        val calls = Calls()
        val noEmail = verifiedIdentity.copy(email = null)

        val result = useCase(verifyResult = Result.success(noEmail), calls = calls).execute(command)

        assertIs<InvalidAppleTokenException>(result.exceptionOrNull())
        assertTrue(calls.creations.isEmpty())
        assertNull(result.getOrNull())
    }
}
