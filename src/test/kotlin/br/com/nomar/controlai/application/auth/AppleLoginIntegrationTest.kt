package br.com.nomar.controlai.application.auth

import br.com.nomar.controlai.domain.auth.entity.AppleIdentity
import br.com.nomar.controlai.domain.auth.entity.User
import br.com.nomar.controlai.domain.auth.exception.InvalidAppleTokenException
import br.com.nomar.controlai.domain.auth.gateway.AppleIdentityVerifierGateway
import br.com.nomar.controlai.domain.auth.gateway.AppleTokenExchangeGateway
import br.com.nomar.controlai.domain.auth.gateway.CreateUserWithPersonalGroupGateway
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.`when`
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

// POST /auth/apple — the Apple verifier and the code exchange are replaced with mocks, so tests never
// call Apple's servers. Database, Flyway, Spring Security and the token issuer run for real.
@SpringBootTest
@AutoConfigureMockMvc
class AppleLoginIntegrationTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var jdbcTemplate: JdbcTemplate
    @Autowired private lateinit var createUserWithPersonalGroupGateway: CreateUserWithPersonalGroupGateway

    @MockitoBean private lateinit var appleIdentityVerifierGateway: AppleIdentityVerifierGateway
    @MockitoBean private lateinit var appleTokenExchangeGateway: AppleTokenExchangeGateway

    private val testEmailPrefix = "apple-it-"
    private val newEmail = "apple-it-new@icloud.com"
    private val existingEmail = "apple-it-existing@example.com"
    private val relayEmail = "apple-it-relay@privaterelay.appleid.com"
    private val appleSub = "apple-sub-integration-001"

    @BeforeEach
    @AfterEach
    fun cleanTestData() {
        val groupIds = jdbcTemplate.queryForList(
            "SELECT gm.group_id FROM group_members gm JOIN users u ON gm.user_id = u.id WHERE u.email LIKE ?",
            Long::class.java,
            "$testEmailPrefix%",
        )
        jdbcTemplate.update("DELETE FROM refresh_tokens WHERE user_id IN (SELECT id FROM users WHERE email LIKE ?)", "$testEmailPrefix%")
        if (groupIds.isNotEmpty()) {
            val placeholders = groupIds.joinToString(",") { "?" }
            jdbcTemplate.update("DELETE FROM categories WHERE group_id IN ($placeholders)", *groupIds.toTypedArray())
            jdbcTemplate.update("DELETE FROM holders WHERE group_id IN ($placeholders)", *groupIds.toTypedArray())
        }
        jdbcTemplate.update("DELETE FROM group_members WHERE user_id IN (SELECT id FROM users WHERE email LIKE ?)", "$testEmailPrefix%")
        if (groupIds.isNotEmpty()) {
            val placeholders = groupIds.joinToString(",") { "?" }
            jdbcTemplate.update("DELETE FROM `groups` WHERE id IN ($placeholders)", *groupIds.toTypedArray())
        }
        jdbcTemplate.update("DELETE FROM user_identities WHERE user_id IN (SELECT id FROM users WHERE email LIKE ?)", "$testEmailPrefix%")
        jdbcTemplate.update("DELETE FROM users WHERE email LIKE ?", "$testEmailPrefix%")
    }

    @Test
    fun `conta nova e criada com grupo pessoal, identidade Apple e refresh token cifrado`() {
        verifiedIdentity(newEmail)
        `when`(appleTokenExchangeGateway.exchangeAuthorizationCode(anyString(), anyString()))
            .thenReturn(Result.success("apple-refresh-plaintext-xyz"))

        login(identityToken = "id-token", nonce = "nonce-1", fullName = "Maria Souza")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.accessToken").isString)
            .andExpect(jsonPath("$.user.email").value(newEmail))
            .andExpect(jsonPath("$.user.name").value("Maria Souza"))

        assertEquals(1, countUsers(newEmail))
        assertEquals(1, countGroupMembershipsFor(newEmail))
        val identity = identityRow(newEmail)
        assertEquals("APPLE", identity["provider"])
        assertEquals(appleSub, identity["subject"])
        val stored = identity["apple_refresh_token_enc"] as String
        assertNotNull(stored)
        assertFalse(stored.contains("apple-refresh-plaintext-xyz"))
    }

    @Test
    fun `segundo login com a mesma identidade devolve a mesma conta sem duplicar e substitui o refresh token`() {
        verifiedIdentity(newEmail)
        `when`(appleTokenExchangeGateway.exchangeAuthorizationCode(anyString(), anyString()))
            .thenReturn(Result.success("apple-refresh-token"))

        login(identityToken = "id-token", nonce = "nonce-1", fullName = "Maria Souza").andExpect(status().isOk)
        val firstUserId = userIdByEmail(newEmail)
        login(identityToken = "id-token", nonce = "nonce-2", fullName = null).andExpect(status().isOk)

        assertEquals(1, countUsers(newEmail))
        assertEquals(firstUserId, userIdByEmail(newEmail))
        verify(appleTokenExchangeGateway, org.mockito.Mockito.times(2)).exchangeAuthorizationCode(anyString(), anyString())
    }

    @Test
    fun `e-mail verificado de conta existente vincula a Apple sem criar conta nova`() {
        insertUser(existingEmail)
        verifiedIdentity(existingEmail)
        `when`(appleTokenExchangeGateway.exchangeAuthorizationCode(anyString(), anyString()))
            .thenReturn(Result.success("apple-refresh-token"))
        val existingId = userIdByEmail(existingEmail)

        login(identityToken = "id-token", nonce = "nonce-1", fullName = null).andExpect(status().isOk)

        assertEquals(1, countUsers(existingEmail))
        assertEquals(existingId, userIdByEmail(existingEmail))
        assertEquals("APPLE", identityRow(existingEmail)["provider"])
    }

    @Test
    fun `e-mail relay cria conta nova e nunca vincula a conta existente`() {
        insertUser(existingEmail)
        verifiedRelayIdentity(relayEmail)
        `when`(appleTokenExchangeGateway.exchangeAuthorizationCode(anyString(), anyString()))
            .thenReturn(Result.success("apple-refresh-token"))
        val existingId = userIdByEmail(existingEmail)

        login(identityToken = "id-token", nonce = "nonce-1", fullName = null).andExpect(status().isOk)

        assertEquals(existingId, userIdByEmail(existingEmail))
        assertEquals(0, countIdentities(existingEmail))
        assertEquals(1, countUsers(relayEmail))
        assertEquals("apple-it-relay", userNameByEmail(relayEmail))
    }

    @Test
    fun `falha na verificacao do identityToken retorna 401 sem trocar codigo nem criar conta`() {
        `when`(appleIdentityVerifierGateway.verify(anyString(), any())).thenReturn(Result.failure(InvalidAppleTokenException()))

        login(identityToken = "id-token", nonce = "wrong-nonce", fullName = "Maria").andExpect(status().isUnauthorized)

        assertEquals(0, countUsers(newEmail))
        verify(appleTokenExchangeGateway, never()).exchangeAuthorizationCode(anyString(), anyString())
    }

    @Test
    fun `codigo de autorizacao invalido retorna 401 e nao cria usuario`() {
        verifiedIdentity(newEmail)
        `when`(appleTokenExchangeGateway.exchangeAuthorizationCode(anyString(), anyString()))
            .thenReturn(Result.failure(InvalidAppleTokenException()))

        login(identityToken = "id-token", nonce = "nonce-1", fullName = "Maria").andExpect(status().isUnauthorized)

        assertEquals(0, countUsers(newEmail))
        assertEquals(0, countIdentities(newEmail))
    }

    @Test
    fun `identityToken com campos obrigatorios ausentes retorna 400`() {
        mockMvc.perform(
            post("/auth/apple")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"identityToken":"","authorizationCode":"code","nonce":"n"}"""),
        ).andExpect(status().isBadRequest)
    }

    private fun verifiedIdentity(email: String) {
        `when`(appleIdentityVerifierGateway.verify(anyString(), any())).thenReturn(
            Result.success(
                AppleIdentity(
                    subject = appleSub,
                    email = email,
                    emailVerified = true,
                    isPrivateRelay = false,
                    audience = "test-apple-bundle-id",
                ),
            ),
        )
    }

    private fun verifiedRelayIdentity(email: String) {
        `when`(appleIdentityVerifierGateway.verify(anyString(), any())).thenReturn(
            Result.success(
                AppleIdentity(
                    subject = appleSub,
                    email = email,
                    emailVerified = true,
                    isPrivateRelay = true,
                    audience = "test-apple-bundle-id",
                ),
            ),
        )
    }

    private fun login(identityToken: String, nonce: String, fullName: String?) =
        mockMvc.perform(
            post("/auth/apple")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    buildString {
                        append("""{"identityToken":"$identityToken","authorizationCode":"auth-code","nonce":"$nonce"""")
                        if (fullName != null) append(""","fullName":"$fullName"""")
                        append("}")
                    },
                ),
        )

    // Goes through the real creation path so the user has its personal group, as every existing account does
    private fun insertUser(email: String) {
        createUserWithPersonalGroupGateway.execute(
            User(name = "apple-it-existing", email = email, passwordHash = "\$2a\$10\$abcdefghijklmnopqrstuuvwxyz0123456789ABCDEFGHIJKLMNOPQ"),
        ).getOrThrow()
    }

    private fun countUsers(email: String): Int =
        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users WHERE email = ?", Int::class.java, email)!!

    private fun countIdentities(email: String): Int =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM user_identities ui JOIN users u ON ui.user_id = u.id WHERE u.email = ?",
            Int::class.java,
            email,
        )!!

    private fun countGroupMembershipsFor(email: String): Int =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM group_members gm JOIN users u ON gm.user_id = u.id WHERE u.email = ?",
            Int::class.java,
            email,
        )!!

    private fun userIdByEmail(email: String): Long =
        jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long::class.java, email)!!

    private fun userNameByEmail(email: String): String =
        jdbcTemplate.queryForObject("SELECT name FROM users WHERE email = ?", String::class.java, email)!!

    private fun identityRow(email: String): Map<String, Any?> =
        jdbcTemplate.queryForMap(
            "SELECT ui.provider, ui.subject, ui.apple_refresh_token_enc FROM user_identities ui JOIN users u ON ui.user_id = u.id WHERE u.email = ?",
            email,
        )
}
