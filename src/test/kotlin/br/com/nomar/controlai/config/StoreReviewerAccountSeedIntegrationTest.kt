package br.com.nomar.controlai.config

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

// Verifies the effect of migration V40 (Tarefa 1.0 do PRD "Publicacao nas Lojas"): a fixed
// account the App Store / Google Play review teams can log in with, since public registration
// is disabled. The real password is never asserted here (it lives out-of-band, not in the
// repo) — this only checks the seed produced a usable, unblocked account.
//
// Only assertions on `groups`/`users`/`group_members`/`subscriptions` are made here: most
// integration tests wipe every group's financial data in their setup (TestDatabaseCleaner),
// which includes the reviewer's example cards/purchases (V42), so those are not a stable
// invariant across the full suite. Their presence is covered by manual verification
// (Tarefa 1.0, subtarefa 1.4) against a real environment instead.
@SpringBootTest
@AutoConfigureMockMvc
class StoreReviewerAccountSeedIntegrationTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var jdbcTemplate: JdbcTemplate
    @Autowired private lateinit var jwtEncoder: JwtEncoder

    private fun reviewerGroupId(): Long =
        jdbcTemplate.queryForObject(
            "SELECT id FROM `groups` WHERE name = 'ControlAI Revisor'",
            Long::class.java,
        )!!

    private fun buildToken(groupId: Long): String {
        val now = Instant.now()
        val claims = JwtClaimsSet.builder()
            .subject("store-reviewer-test")
            .issuedAt(now)
            .expiresAt(now.plus(Duration.ofHours(1)))
            .claim("groupId", groupId)
            .claim("email", "revisor.loja@nomar.com.br")
            .build()
        return jwtEncoder
            .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
            .tokenValue
    }

    @Test
    fun `V40 seeds a reviewer account with a real group, user and active subscription`() {
        val groupId = assertDoesNotThrow { reviewerGroupId() }

        val userEmail = jdbcTemplate.queryForObject(
            "SELECT u.email FROM users u JOIN group_members gm ON gm.user_id = u.id WHERE gm.group_id = ?",
            String::class.java,
            groupId,
        )
        assertEquals("revisor.loja@nomar.com.br", userEmail)

        val passwordHash = jdbcTemplate.queryForObject(
            "SELECT password_hash FROM users WHERE email = ?",
            String::class.java,
            "revisor.loja@nomar.com.br",
        )
        assertNotNull(passwordHash)
        assertTrue(passwordHash.startsWith("\$2"), "password_hash deve ser um hash BCrypt valido")

        val subscriptionStatus = jdbcTemplate.queryForObject(
            "SELECT status FROM subscriptions WHERE group_id = ?",
            String::class.java,
            groupId,
        )
        assertEquals("ACTIVE", subscriptionStatus)
    }

    @Test
    fun `GET purchases returns 200 for the seeded reviewer account, not blocked by SubscriptionGuardFilter`() {
        val token = buildToken(reviewerGroupId())

        mockMvc.perform(get("/purchases").header("Authorization", "Bearer $token"))
            .andExpect(status().isOk)
    }
}
