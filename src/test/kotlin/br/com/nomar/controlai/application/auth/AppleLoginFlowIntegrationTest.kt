package br.com.nomar.controlai.application.auth

import br.com.nomar.controlai.domain.auth.TokenHasher
import br.com.nomar.controlai.domain.auth.entity.AppleIdentity
import br.com.nomar.controlai.domain.auth.gateway.AppleIdentityVerifierGateway
import br.com.nomar.controlai.domain.auth.gateway.AppleTokenExchangeGateway
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.`when`
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.Base64
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

// Web and Android login through /auth/apple/start, /callback and /session. Apple's verifier and code exchange
// are mocked, so no call reaches Apple. The database, Flyway, Spring Security and the token issuer are real.
@SpringBootTest
@AutoConfigureMockMvc
class AppleLoginFlowIntegrationTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var jdbcTemplate: JdbcTemplate

    @MockitoBean private lateinit var appleIdentityVerifierGateway: AppleIdentityVerifierGateway
    @MockitoBean private lateinit var appleTokenExchangeGateway: AppleTokenExchangeGateway

    private val newEmail = "apple-flow-new@icloud.com"
    private val appleSub = "apple-sub-flow-001"
    private val errorRedirect = "error=apple_login_failed"

    @BeforeEach
    @AfterEach
    fun cleanTestData() {
        jdbcTemplate.update("DELETE FROM apple_login_flows")
        jdbcTemplate.update("DELETE FROM refresh_tokens WHERE user_id IN (SELECT id FROM users WHERE email LIKE ?)", "apple-flow-%")
        jdbcTemplate.update("DELETE FROM user_identities WHERE user_id IN (SELECT id FROM users WHERE email LIKE ?)", "apple-flow-%")
        jdbcTemplate.update("DELETE FROM group_members WHERE user_id IN (SELECT id FROM users WHERE email LIKE ?)", "apple-flow-%")
        jdbcTemplate.update("DELETE FROM users WHERE email LIKE ?", "apple-flow-%")
    }

    @Test
    fun `web - callback redireciona com codigo de uso unico e a troca gera a sessao uma vez`() {
        val nonce = startNonce("WEB")
        verifiedIdentity(newEmail)
        `when`(appleTokenExchangeGateway.exchangeAuthorizationCode(anyString(), anyString()))
            .thenReturn(Result.success("apple-refresh-token"))

        val location = callback(nonce, fullName = "Maria Souza").andExpect(status().isSeeOther)
            .andReturn().response.getHeader("Location")
        val code = codeFrom(location.orEmpty())
        assertTrue(location.orEmpty().contains("/login?code="))

        session(code)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.accessToken").isString)
            .andExpect(jsonPath("$.user.email").value(newEmail))
            .andExpect(jsonPath("$.user.name").value("Maria Souza"))

        session(code).andExpect(status().isUnauthorized)
    }

    @Test
    fun `android - o callback redireciona para o deep link configurado`() {
        val nonce = startNonce("ANDROID")
        verifiedIdentity(newEmail)
        `when`(appleTokenExchangeGateway.exchangeAuthorizationCode(anyString(), anyString()))
            .thenReturn(Result.success("apple-refresh-token"))

        callback(nonce, fullName = null)
            .andExpect(status().isSeeOther)
            .andExpect(header().string("Location", org.hamcrest.Matchers.containsString("auth/apple-login?code=")))
    }

    @Test
    fun `o nonce vai para o verificador e o codigo entregue fica so como hash no banco`() {
        val nonce = startNonce("WEB")
        verifiedIdentity(newEmail)
        `when`(appleTokenExchangeGateway.exchangeAuthorizationCode(anyString(), anyString()))
            .thenReturn(Result.success("apple-refresh-token"))

        val code = codeFrom(callback(nonce, fullName = null).andReturn().response.getHeader("Location").orEmpty())

        verify(appleIdentityVerifierGateway).verify(anyString(), eq(nonce))
        val storedHash = jdbcTemplate.queryForObject(
            "SELECT handoff_code_hash FROM apple_login_flows WHERE nonce = ?",
            String::class.java,
            nonce,
        )
        assertEquals(TokenHasher.sha256(code), storedHash)
        assertNotNull(storedHash)
        assertTrue(!storedHash.contains(code))
    }

    @Test
    fun `replay do callback com o mesmo id_token nao gera segundo codigo`() {
        val nonce = startNonce("WEB")
        verifiedIdentity(newEmail)
        `when`(appleTokenExchangeGateway.exchangeAuthorizationCode(anyString(), anyString()))
            .thenReturn(Result.success("apple-refresh-token"))

        callback(nonce, fullName = null).andExpect(status().isSeeOther)
        callback(nonce, fullName = null)
            .andExpect(status().isSeeOther)
            .andExpect(header().string("Location", org.hamcrest.Matchers.containsString(errorRedirect)))

        verify(appleTokenExchangeGateway, times(1)).exchangeAuthorizationCode(anyString(), anyString())
    }

    @Test
    fun `nonce desconhecido redireciona com erro e nao cria conta`() {
        verifiedIdentity(newEmail)

        callback("nonce-that-was-never-issued", fullName = null)
            .andExpect(status().isSeeOther)
            .andExpect(header().string("Location", org.hamcrest.Matchers.containsString(errorRedirect)))

        assertEquals(0, countUsers(newEmail))
    }

    @Test
    fun `codigo de uso unico expirado nao gera sessao`() {
        val nonce = startNonce("WEB")
        verifiedIdentity(newEmail)
        `when`(appleTokenExchangeGateway.exchangeAuthorizationCode(anyString(), anyString()))
            .thenReturn(Result.success("apple-refresh-token"))
        val code = codeFrom(callback(nonce, fullName = null).andReturn().response.getHeader("Location").orEmpty())

        jdbcTemplate.update(
            "UPDATE apple_login_flows SET handoff_expires_at = NOW() - INTERVAL 1 MINUTE WHERE handoff_code_hash = ?",
            TokenHasher.sha256(code),
        )

        session(code).andExpect(status().isUnauthorized)
    }

    @Test
    fun `codigo inventado retorna 401`() {
        session("codigo-inventado").andExpect(status().isUnauthorized)
    }

    private fun startNonce(platform: String): String {
        val result = mockMvc.perform(
            post("/auth/apple/start")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"platform":"$platform"}"""),
        ).andExpect(status().isOk).andReturn()
        return Regex("\"nonce\":\"([0-9a-f]+)\"").find(result.response.contentAsString)!!.groupValues[1]
    }

    private fun callback(nonce: String, fullName: String?): ResultActions {
        val user = fullName?.let { """{"name":{"firstName":"${it.substringBefore(' ')}","lastName":"${it.substringAfter(' ')}"}}""" }
        var request = post("/auth/apple/callback")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .param("code", "apple-auth-code")
            .param("id_token", unsignedIdToken(TokenHasher.sha256(nonce)))
            .param("state", "apple-generated-state")
        if (user != null) request = request.param("user", user)
        return mockMvc.perform(request)
    }

    private fun session(code: String): ResultActions =
        mockMvc.perform(
            post("/auth/apple/session")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"code":"$code"}"""),
        )

    // The backend reads the nonce claim before verifying, so a plain unsigned token with that claim is enough here
    private fun unsignedIdToken(nonceDigest: String): String {
        val encoder = Base64.getUrlEncoder().withoutPadding()
        val header = encoder.encodeToString("""{"alg":"none"}""".toByteArray())
        val payload = encoder.encodeToString("""{"sub":"$appleSub","nonce":"$nonceDigest"}""".toByteArray())
        return "$header.$payload."
    }

    private fun codeFrom(location: String): String =
        location.substringAfter("code=").substringBefore("&")

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

    private fun countUsers(email: String): Int =
        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users WHERE email = ?", Int::class.java, email)!!
}
