package br.com.nomar.controlai.application.auth

import br.com.nomar.controlai.domain.auth.gateway.EmailGateway
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.lenient
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.anyString
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import java.time.Duration
import java.time.Instant
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

// Integration tests for the forgot/reset/change password flows.
// EmailGateway is replaced with a Mockito mock so no actual emails are sent.
@SpringBootTest
@AutoConfigureMockMvc
class ForgotResetPasswordIntegrationTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var jdbcTemplate: JdbcTemplate
    @Autowired private lateinit var jwtEncoder: JwtEncoder

    @MockitoBean private lateinit var emailGateway: EmailGateway

    private val testEmail = "password-reset-test@controlai.test"
    private var testUserId: Long = 0L
    private var testGroupId: Long = 0L

    @BeforeEach
    fun setupTestUser() {
        cleanTestUser()

        jdbcTemplate.update("INSERT INTO `groups` (name) VALUES (?)", "TestGroupReset")
        testGroupId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long::class.java)!!

        jdbcTemplate.update(
            "INSERT INTO users (name, email, password_hash) VALUES (?, ?, ?)",
            "Reset User",
            testEmail,
            "\$2a\$10\$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy",
        )
        testUserId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long::class.java)!!
        jdbcTemplate.update("INSERT INTO group_members (group_id, user_id) VALUES (?, ?)", testGroupId, testUserId)

        // Lenient: some tests (e.g. changePassword) don't trigger email at all
        lenient().`when`(emailGateway.sendPasswordReset(anyString(), anyString(), anyString()))
            .thenAnswer { Result.success(Unit) }
    }

    @AfterEach
    fun cleanTestUser() {
        if (testUserId > 0) {
            jdbcTemplate.update("DELETE FROM password_reset_tokens WHERE user_id = ?", testUserId)
            jdbcTemplate.update("DELETE FROM refresh_tokens WHERE user_id = ?", testUserId)
        }
        if (testGroupId > 0) {
            jdbcTemplate.update("DELETE FROM categories WHERE group_id = ?", testGroupId)
        }
        jdbcTemplate.update("DELETE FROM group_members WHERE user_id IN (SELECT id FROM users WHERE email = ?)", testEmail)
        if (testGroupId > 0) {
            jdbcTemplate.update("DELETE FROM `groups` WHERE id = ?", testGroupId)
        }
        jdbcTemplate.update("DELETE FROM users WHERE email = ?", testEmail)
        testUserId = 0L
        testGroupId = 0L
    }

    // ---- POST /auth/password/forgot ----

    @Test
    fun `forgot password returns 204 for existing email`() {
        mockMvc.perform(
            post("/auth/password/forgot")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$testEmail"}"""),
        ).andExpect(status().isNoContent)
    }

    @Test
    fun `forgot password returns 204 even for non-existent email (does not reveal existence)`() {
        mockMvc.perform(
            post("/auth/password/forgot")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"nobody@controlai.test"}"""),
        ).andExpect(status().isNoContent)
    }

    @Test
    fun `forgot password stores reset token in DB and sends email`() {
        mockMvc.perform(
            post("/auth/password/forgot")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$testEmail"}"""),
        ).andExpect(status().isNoContent)

        val count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM password_reset_tokens WHERE user_id = ?",
            Int::class.java,
            testUserId,
        )
        assertTrue(count == 1)

        verify(emailGateway, times(1)).sendPasswordReset(anyString(), anyString(), anyString())
    }

    @Test
    fun `forgot password does not send email for non-existent user`() {
        mockMvc.perform(
            post("/auth/password/forgot")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"ghost@controlai.test"}"""),
        ).andExpect(status().isNoContent)

        verify(emailGateway, times(0)).sendPasswordReset(anyString(), anyString(), anyString())
    }

    @Test
    fun `forgot password returns 204 even when email service fails`() {
        // Override the default stub to return failure
        lenient().`when`(emailGateway.sendPasswordReset(anyString(), anyString(), anyString()))
            .thenAnswer { Result.failure<Unit>(RuntimeException("Resend is down")) }

        mockMvc.perform(
            post("/auth/password/forgot")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$testEmail"}"""),
        ).andExpect(status().isNoContent)
    }

    @Test
    fun `forgot password returns 400 for blank email`() {
        mockMvc.perform(
            post("/auth/password/forgot")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":""}"""),
        ).andExpect(status().isBadRequest)
    }

    // ---- POST /auth/password/reset ----

    // Uses thenAnswer instead of ArgumentCaptor.capture() to avoid Kotlin null-safety NPE:
    // capture() returns null at runtime but Kotlin's non-null intrinsic on String params throws before
    // Mockito can register the matcher, leaving the queue dirty for subsequent tests.
    private fun triggerForgotAndGetToken(): String {
        var capturedLink: String? = null
        lenient().`when`(emailGateway.sendPasswordReset(anyString(), anyString(), anyString()))
            .thenAnswer { invocation ->
                capturedLink = invocation.getArgument(2)
                Result.success(Unit)
            }

        mockMvc.perform(
            post("/auth/password/forgot")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$testEmail"}"""),
        ).andExpect(status().isNoContent)

        val link = capturedLink ?: error("sendPasswordReset was not called or link was not captured")
        return link.substringAfterLast("token=")
    }

    @Test
    fun `reset password returns 204 for valid token`() {
        val rawToken = triggerForgotAndGetToken()

        mockMvc.perform(
            post("/auth/password/reset")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"token":"$rawToken","newPassword":"newSecurePass123"}"""),
        ).andExpect(status().isNoContent)
    }

    @Test
    fun `reset password updates user password_hash in DB`() {
        val rawToken = triggerForgotAndGetToken()
        val passwordBefore = jdbcTemplate.queryForObject(
            "SELECT password_hash FROM users WHERE id = ?",
            String::class.java,
            testUserId,
        )

        mockMvc.perform(
            post("/auth/password/reset")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"token":"$rawToken","newPassword":"newSecurePass123"}"""),
        ).andExpect(status().isNoContent)

        val passwordAfter = jdbcTemplate.queryForObject(
            "SELECT password_hash FROM users WHERE id = ?",
            String::class.java,
            testUserId,
        )
        assertNotNull(passwordAfter)
        assertTrue(passwordAfter != passwordBefore)
    }

    @Test
    fun `reset password marks token as used (single-use enforcement)`() {
        val rawToken = triggerForgotAndGetToken()

        mockMvc.perform(
            post("/auth/password/reset")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"token":"$rawToken","newPassword":"newSecurePass123"}"""),
        ).andExpect(status().isNoContent)

        // Second use of the same token must be rejected
        mockMvc.perform(
            post("/auth/password/reset")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"token":"$rawToken","newPassword":"anotherNewPass456"}"""),
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `reset password returns 400 for unknown token`() {
        mockMvc.perform(
            post("/auth/password/reset")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"token":"00000000-0000-0000-0000-000000000000","newPassword":"newSecurePass123"}"""),
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `reset password returns 400 when new password is too short`() {
        mockMvc.perform(
            post("/auth/password/reset")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"token":"any-token","newPassword":"short"}"""),
        ).andExpect(status().isBadRequest)
    }

    // ---- PUT /me/password (ChangePassword) ----

    private fun buildBearerToken(userId: Long, groupId: Long): String {
        val now = Instant.now()
        val claims = JwtClaimsSet.builder()
            .subject(userId.toString())
            .issuedAt(now)
            .expiresAt(now.plus(Duration.ofMinutes(15)))
            .claim("groupId", groupId)
            .claim("email", testEmail)
            .build()
        return jwtEncoder
            .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
            .tokenValue
    }

    @Test
    fun `change password returns 204 when current password is correct`() {
        // Set a known password via reset flow
        val rawToken = triggerForgotAndGetToken()
        val knownPassword = "knownPass456"
        mockMvc.perform(
            post("/auth/password/reset")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"token":"$rawToken","newPassword":"$knownPassword"}"""),
        ).andExpect(status().isNoContent)

        val token = buildBearerToken(testUserId, testGroupId)
        mockMvc.perform(
            put("/me/password")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"currentPassword":"$knownPassword","newPassword":"brandNewPass789"}"""),
        ).andExpect(status().isNoContent)
    }

    @Test
    fun `change password returns 401 when current password is wrong`() {
        val token = buildBearerToken(testUserId, testGroupId)
        mockMvc.perform(
            put("/me/password")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"currentPassword":"wrongpassword","newPassword":"brandNewPass789"}"""),
        ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `change password returns 400 when new password is too short`() {
        val token = buildBearerToken(testUserId, testGroupId)
        mockMvc.perform(
            put("/me/password")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"currentPassword":"initialpassword123","newPassword":"short"}"""),
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `change password returns 401 without auth token`() {
        mockMvc.perform(
            put("/me/password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"currentPassword":"any","newPassword":"brandNewPass789"}"""),
        ).andExpect(status().isUnauthorized)
    }
}
