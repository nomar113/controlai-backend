package br.com.nomar.controlai.application.payments_notification

import br.com.nomar.controlai.domain.auth.TokenHasher
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

// Uses the auto-configured MockMvc which applies a default Bearer token for all requests.
// For the API key tests we bypass that by sending X-Api-Key without any Bearer token,
// which exercises the ApiKeyAuthFilter independently of the JWT resource server.
@SpringBootTest
@AutoConfigureMockMvc
class ApiKeyAuthIntegrationTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var jdbcTemplate: JdbcTemplate

    private val rawKey = "cap_integrationtestkey12345678901234"
    private val keyHash = TokenHasher.sha256(rawKey)

    @BeforeEach
    fun setUp() {
        jdbcTemplate.update("DELETE FROM api_keys")
        // Insert a valid (non-revoked) API key for group 1
        jdbcTemplate.update(
            "INSERT INTO api_keys (group_id, key_hash, label) VALUES (1, ?, 'Test Key')",
            keyHash,
        )
    }

    @AfterEach
    fun tearDown() {
        jdbcTemplate.update("DELETE FROM api_keys")
    }

    @Test
    fun `POST notification with valid X-Api-Key should return 201`() {
        mockMvc.perform(
            post("/payments/notification")
                .header("X-Api-Key", rawKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"text": "Compra aprovada no cartao final 1234 em 01/01/2026 10:00. Valor de R$ 10,00 em 1x Padaria.", "origin": "sms-app"}"""),
        ).andExpect(status().isCreated)
    }

    @Test
    fun `POST notification without X-Api-Key should return 401`() {
        mockMvc.perform(
            post("/payments/notification")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"text": "Compra aprovada no cartao final 1234 em 01/01/2026 10:00. Valor de R$ 10,00 em 1x Padaria.", "origin": "sms-app"}"""),
        ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `POST notification with wrong X-Api-Key should return 401`() {
        mockMvc.perform(
            post("/payments/notification")
                .header("X-Api-Key", "cap_wrongkeyvalue")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"text": "Compra aprovada no cartao final 1234 em 01/01/2026 10:00. Valor de R$ 10,00 em 1x Padaria.", "origin": "sms-app"}"""),
        ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `POST notification with revoked X-Api-Key should return 401`() {
        jdbcTemplate.update(
            "UPDATE api_keys SET revoked_at = NOW() WHERE key_hash = ?",
            keyHash,
        )

        mockMvc.perform(
            post("/payments/notification")
                .header("X-Api-Key", rawKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"text": "Compra aprovada no cartao final 1234 em 01/01/2026 10:00. Valor de R$ 10,00 em 1x Padaria.", "origin": "sms-app"}"""),
        ).andExpect(status().isUnauthorized)
    }
}
