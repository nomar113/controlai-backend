package br.com.nomar.controlai.config

import br.com.nomar.controlai.application.billing.application.UpsertSubscriptionProvider
import br.com.nomar.controlai.domain.auth.TokenHasher
import br.com.nomar.controlai.domain.billing.entity.Subscription
import br.com.nomar.controlai.domain.billing.entity.SubscriptionPlan
import br.com.nomar.controlai.domain.billing.entity.SubscriptionStatus
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Duration
import java.time.Instant

// End-to-end coverage of SubscriptionGuardFilter against a real protected endpoint (GET
// /purchases), exercising the gate for each subscription state plus the public-route bypass.
@SpringBootTest
@AutoConfigureMockMvc
class SubscriptionGuardFilterIntegrationTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var jdbcTemplate: JdbcTemplate
    @Autowired private lateinit var jwtEncoder: JwtEncoder
    @Autowired private lateinit var upsertSubscriptionProvider: UpsertSubscriptionProvider

    private val createdGroupIds = mutableListOf<Long>()

    @AfterEach
    fun tearDown() {
        createdGroupIds.forEach { groupId ->
            jdbcTemplate.update("DELETE FROM api_keys WHERE group_id = ?", groupId)
            jdbcTemplate.update("DELETE FROM subscriptions WHERE group_id = ?", groupId)
            jdbcTemplate.update("DELETE FROM `groups` WHERE id = ?", groupId)
        }
        createdGroupIds.clear()
    }

    private fun createGroup(): Long {
        jdbcTemplate.update("INSERT INTO `groups` (name) VALUES ('Grupo Teste Subscription Guard')")
        val groupId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long::class.java)!!
        createdGroupIds.add(groupId)
        return groupId
    }

    private fun buildToken(groupId: Long): String {
        val now = Instant.now()
        val claims = JwtClaimsSet.builder()
            .subject("1")
            .issuedAt(now)
            .expiresAt(now.plus(Duration.ofHours(1)))
            .claim("groupId", groupId)
            .claim("email", "subscription-guard-test@controlai.dev")
            .build()
        return jwtEncoder
            .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
            .tokenValue
    }

    @Test
    fun `GET purchases returns 200 for a group with an active subscription`() {
        val groupId = createGroup()
        upsertSubscriptionProvider.execute(
            Subscription(groupId = groupId, plan = SubscriptionPlan.ANNUAL, status = SubscriptionStatus.ACTIVE),
        )
        val token = buildToken(groupId)

        mockMvc.perform(get("/purchases").header("Authorization", "Bearer $token"))
            .andExpect(status().isOk)
    }

    @Test
    fun `GET purchases returns 200 for a grandfathered group`() {
        val groupId = createGroup()
        upsertSubscriptionProvider.execute(
            Subscription(groupId = groupId, plan = SubscriptionPlan.GRANDFATHERED, status = SubscriptionStatus.ACTIVE),
        )
        val token = buildToken(groupId)

        mockMvc.perform(get("/purchases").header("Authorization", "Bearer $token"))
            .andExpect(status().isOk)
    }

    @Test
    fun `GET purchases returns 402 for a group without any subscription row`() {
        val groupId = createGroup()
        val token = buildToken(groupId)

        mockMvc.perform(get("/purchases").header("Authorization", "Bearer $token"))
            .andExpect(status().`is`(402))
    }

    @Test
    fun `GET purchases returns 402 for a group with a cancelled subscription`() {
        val groupId = createGroup()
        upsertSubscriptionProvider.execute(
            Subscription(groupId = groupId, plan = SubscriptionPlan.ANNUAL, status = SubscriptionStatus.CANCELLED),
        )
        val token = buildToken(groupId)

        mockMvc.perform(get("/purchases").header("Authorization", "Bearer $token"))
            .andExpect(status().`is`(402))
    }

    @Test
    fun `GET health is reachable without any authentication or subscription check`() {
        mockMvc.perform(get("/health"))
            .andExpect(status().isOk)
    }

    @Test
    fun `POST payments notification returns 402 for an API-key group without an active subscription`() {
        val groupId = createGroup()
        val rawKey = "cap_subscriptionguardtestkey1234567890"
        jdbcTemplate.update(
            "INSERT INTO api_keys (group_id, key_hash, label) VALUES (?, ?, 'Subscription Guard Test Key')",
            groupId,
            TokenHasher.sha256(rawKey),
        )

        mockMvc.perform(
            post("/payments/notification")
                .header("X-Api-Key", rawKey)
                // Overrides the auto-configured MockMvc's default Bearer token (for group 1,
                // which is grandfathered/active) so only the API key resolves an authentication,
                // exercising the ApiKeyAuthentication branch of the gate in isolation.
                .header("Authorization", "")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"text": "Compra aprovada no cartao final 1234 em 01/01/2026 10:00. Valor de R$ 10,00 em 1x Padaria.", "origin": "sms-app"}"""),
        ).andExpect(status().`is`(402))
    }
}
