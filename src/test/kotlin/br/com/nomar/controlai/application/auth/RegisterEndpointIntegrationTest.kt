package br.com.nomar.controlai.application.auth

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import kotlin.test.assertEquals

// Public registration was retired in favor of Kiwify-driven onboarding (Tarefa 5.0/7.0).
// These tests confirm POST /auth/register no longer creates accounts, and that the
// automatic onboarding flow used by the Kiwify webhook remains unaffected (regression
// coverage for the actual account-creation path lives in KiwifyWebhookControllerIntegrationTest,
// which exercises HandleKiwifyWebhookUseCase directly and does not depend on this endpoint).
@SpringBootTest
@AutoConfigureMockMvc
class RegisterEndpointIntegrationTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var jdbcTemplate: JdbcTemplate

    private val testEmail = "register-retired-test@controlai.test"

    @AfterEach
    fun cleanup() {
        jdbcTemplate.update("DELETE FROM group_members WHERE user_id IN (SELECT id FROM users WHERE email = ?)", testEmail)
        jdbcTemplate.update("DELETE FROM users WHERE email = ?", testEmail)
    }

    @Test
    fun `POST auth register returns 410 Gone`() {
        mockMvc.perform(
            post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Someone","email":"$testEmail","password":"anySecurePass1"}"""),
        ).andExpect(status().isGone)
    }

    @Test
    fun `POST auth register does not create a user`() {
        mockMvc.perform(
            post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Someone","email":"$testEmail","password":"anySecurePass1"}"""),
        ).andExpect(status().isGone)

        val count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM users WHERE email = ?",
            Int::class.java,
            testEmail,
        )
        assertEquals(0, count)
    }

    @Test
    fun `POST auth register returns 410 Gone even with an empty body`() {
        mockMvc.perform(
            post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"),
        ).andExpect(status().isGone)
    }
}
