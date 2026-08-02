package br.com.nomar.controlai.application.auth

import br.com.nomar.controlai.domain.auth.entity.GoogleUserInfo
import br.com.nomar.controlai.domain.auth.exception.InvalidGoogleTokenException
import br.com.nomar.controlai.domain.auth.gateway.GoogleTokenVerifierGateway
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.anyString
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

// POST /auth/google — GoogleTokenVerifierGateway is replaced with a mock so tests never
// call Google's servers. The rest of the stack (DB, Spring Security, JWT) runs for real.
@SpringBootTest
@AutoConfigureMockMvc
class GoogleLoginIntegrationTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var jdbcTemplate: JdbcTemplate

    @MockitoBean private lateinit var googleTokenVerifierGateway: GoogleTokenVerifierGateway

    private val testEmail = "google-integration@gmail.com"
    private val validGoogleUser = GoogleUserInfo(
        sub = "google-sub-integration-123",
        email = testEmail,
        name = "Integration User",
    )

    @BeforeEach
    @AfterEach
    fun cleanTestUser() {
        // Resolve group_ids belonging to the test user before removing memberships
        val groupIds = jdbcTemplate.queryForList(
            "SELECT gm.group_id FROM group_members gm JOIN users u ON gm.user_id = u.id WHERE u.email = ?",
            Long::class.java,
            testEmail,
        )
        jdbcTemplate.update("DELETE FROM refresh_tokens WHERE user_id IN (SELECT id FROM users WHERE email = ?)", testEmail)
        if (groupIds.isNotEmpty()) {
            val placeholders = groupIds.joinToString(",") { "?" }
            jdbcTemplate.update("DELETE FROM categories WHERE group_id IN ($placeholders)", *groupIds.toTypedArray())
        }
        jdbcTemplate.update("DELETE FROM group_members WHERE user_id IN (SELECT id FROM users WHERE email = ?)", testEmail)
        if (groupIds.isNotEmpty()) {
            val placeholders = groupIds.joinToString(",") { "?" }
            jdbcTemplate.update("DELETE FROM `groups` WHERE id IN ($placeholders)", *groupIds.toTypedArray())
        }
        jdbcTemplate.update("DELETE FROM users WHERE email = ?", testEmail)
    }

    @Test
    fun `valid token creates new account and returns AuthSession`() {
        `when`(googleTokenVerifierGateway.verify(anyString())).thenReturn(Result.success(validGoogleUser))

        mockMvc.perform(
            post("/auth/google")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"idToken":"valid-google-id-token"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.accessToken").isString)
            .andExpect(jsonPath("$.refreshToken").isString)
            .andExpect(jsonPath("$.user.email").value(testEmail))
    }

    @Test
    fun `second login with same google_sub reuses account without duplication`() {
        `when`(googleTokenVerifierGateway.verify(anyString())).thenReturn(Result.success(validGoogleUser))

        repeat(2) {
            mockMvc.perform(
                post("/auth/google")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"idToken":"valid-google-id-token"}"""),
            ).andExpect(status().isOk)
        }

        val count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM users WHERE email = ?",
            Int::class.java,
            testEmail,
        )
        assertEquals(1, count)
    }

    @Test
    fun `google_sub is linked to existing email-password account (RF-1-5)`() {
        // Pre-create a dedicated test group and user with email+password only (no google_sub)
        jdbcTemplate.update("INSERT INTO `groups` (name) VALUES (?)", "TestGroupForLink")
        val groupId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long::class.java)!!
        jdbcTemplate.update(
            "INSERT INTO users (name, email, password_hash) VALUES (?, ?, ?)",
            "Integration User", testEmail, "\$2a\$10\$testhashedpw",
        )
        val userId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long::class.java)!!
        jdbcTemplate.update(
            "INSERT INTO group_members (group_id, user_id) VALUES (?, ?)",
            groupId, userId,
        )

        `when`(googleTokenVerifierGateway.verify(anyString())).thenReturn(Result.success(validGoogleUser))

        mockMvc.perform(
            post("/auth/google")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"idToken":"valid-google-id-token"}"""),
        ).andExpect(status().isOk)

        val googleSub = jdbcTemplate.queryForObject(
            "SELECT google_sub FROM users WHERE email = ?",
            String::class.java,
            testEmail,
        )
        assertEquals("google-sub-integration-123", googleSub)
    }

    @Test
    fun `invalid token returns 401`() {
        `when`(googleTokenVerifierGateway.verify(anyString()))
            .thenReturn(Result.failure(InvalidGoogleTokenException()))

        mockMvc.perform(
            post("/auth/google")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"idToken":"bad-token"}"""),
        ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `blank idToken returns 400`() {
        mockMvc.perform(
            post("/auth/google")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"idToken":""}"""),
        ).andExpect(status().isBadRequest)
    }
}
