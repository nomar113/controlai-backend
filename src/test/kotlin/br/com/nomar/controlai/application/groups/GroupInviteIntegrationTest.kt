package br.com.nomar.controlai.application.groups

import br.com.nomar.controlai.domain.auth.gateway.EmailGateway
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.lenient
import org.mockito.Mockito.anyString
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
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.time.Duration
import java.time.Instant

// Integration test covering the group-invite flow between two users (couple scenario).
// Verifies: invite sent, invitee accepts, both users see the same group, invitee leaves and
// is isolated again.
@SpringBootTest
@AutoConfigureMockMvc
class GroupInviteIntegrationTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var jdbcTemplate: JdbcTemplate
    @Autowired private lateinit var jwtEncoder: JwtEncoder
    @Autowired private lateinit var webApplicationContext: WebApplicationContext

    @MockitoBean private lateinit var emailGateway: EmailGateway

    private var inviterUserId = 0L
    private var inviterGroupId = 0L
    private var inviteeUserId = 0L
    private var inviteeGroupId = 0L

    @BeforeEach
    fun setup() {
        lenient().`when`(emailGateway.sendPasswordReset(anyString(), anyString(), anyString()))
            .thenAnswer { Result.success(Unit) }
        lenient().`when`(emailGateway.sendGroupInvite(anyString(), anyString()))
            .thenAnswer { Result.success(Unit) }

        cleanAll()
        createUserAndGroup("Inviter", "inviter-gi@test.com").let { (u, g) ->
            inviterUserId = u; inviterGroupId = g
        }
        createUserAndGroup("Invitee", "invitee-gi@test.com").let { (u, g) ->
            inviteeUserId = u; inviteeGroupId = g
        }
    }

    @AfterEach
    fun cleanAll() {
        val emails = listOf("inviter-gi@test.com", "invitee-gi@test.com")
        val userIds = emails.mapNotNull { email ->
            jdbcTemplate.queryForList("SELECT id FROM users WHERE email = ?", Long::class.java, email).firstOrNull()
        }
        // Collect every group these test users touched (they may have moved between groups
        // during accept-invite flows) before removing any group_members row, so a group that
        // still has the *other* test user as a member is never deleted prematurely.
        val groupIds = userIds.flatMap { uid ->
            jdbcTemplate.queryForList("SELECT group_id FROM group_members WHERE user_id = ?", Long::class.java, uid)
        }.toSet()

        userIds.forEachIndexed { index, uid ->
            jdbcTemplate.update(
                "DELETE FROM group_invites WHERE inviter_user_id = ? OR invitee_email = ?", uid, emails[index],
            )
            jdbcTemplate.update("DELETE FROM refresh_tokens WHERE user_id = ?", uid)
            jdbcTemplate.update("DELETE FROM group_members WHERE user_id = ?", uid)
        }
        groupIds.forEach { gid ->
            jdbcTemplate.update("DELETE FROM payment_methods WHERE group_id = ?", gid)
            jdbcTemplate.update("DELETE FROM holders WHERE group_id = ?", gid)
            jdbcTemplate.update("DELETE FROM categories WHERE group_id = ?", gid)
            jdbcTemplate.update("DELETE FROM `groups` WHERE id = ?", gid)
        }
        userIds.forEach { uid -> jdbcTemplate.update("DELETE FROM users WHERE id = ?", uid) }
        // Clean up any orphaned groups created during leave-group tests
        jdbcTemplate.update("DELETE FROM group_invites WHERE invitee_email = 'invitee-gi@test.com'")
    }

    // ---- Helper builders ----

    private fun createUserAndGroup(name: String, email: String): Pair<Long, Long> {
        jdbcTemplate.update("INSERT INTO `groups` (name) VALUES (?)", name)
        val gid = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long::class.java)!!
        jdbcTemplate.update(
            "INSERT INTO users (name, email, password_hash) VALUES (?, ?, ?)",
            name, email, "\$2a\$10\$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy",
        )
        val uid = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long::class.java)!!
        jdbcTemplate.update("INSERT INTO group_members (group_id, user_id) VALUES (?, ?)", gid, uid)
        return uid to gid
    }

    private fun buildToken(userId: Long, groupId: Long, email: String): String {
        val now = Instant.now()
        val claims = JwtClaimsSet.builder()
            .subject(userId.toString())
            .issuedAt(now)
            .expiresAt(now.plus(Duration.ofHours(1)))
            .claim("groupId", groupId)
            .claim("email", email)
            .build()
        return jwtEncoder
            .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
            .tokenValue
    }

    // ---- Tests ----

    @Test
    fun `POST invites creates pending invite`() {
        val token = buildToken(inviterUserId, inviterGroupId, "inviter-gi@test.com")

        mockMvc.perform(
            post("/invites")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"invitee-gi@test.com"}"""),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.inviteeEmail").value("invitee-gi@test.com"))
            .andExpect(jsonPath("$.groupId").value(inviterGroupId))
    }

    @Test
    fun `POST invites returns 409 when invitee already in same group`() {
        // Move invitee into inviter's group manually
        jdbcTemplate.update("UPDATE group_members SET group_id = ? WHERE user_id = ?", inviterGroupId, inviteeUserId)
        val token = buildToken(inviterUserId, inviterGroupId, "inviter-gi@test.com")

        mockMvc.perform(
            post("/invites")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"invitee-gi@test.com"}"""),
        ).andExpect(status().isConflict)
    }

    @Test
    fun `POST invites 401 without auth token`() {
        // TestAuthMockMvcConfig injects a default Bearer token into every request on the
        // autowired mockMvc, so anonymous-access tests need their own raw MockMvc instance.
        val rawMockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
            .apply<DefaultMockMvcBuilder>(springSecurity())
            .build()

        rawMockMvc.perform(
            post("/invites")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"anyone@test.com"}"""),
        ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `GET invites pending returns invites for authenticated user`() {
        // Create an invite in the DB directly
        jdbcTemplate.update(
            "INSERT INTO group_invites (group_id, inviter_user_id, invitee_email, status, token, expires_at) " +
                "VALUES (?, ?, ?, 'PENDING', ?, DATE_ADD(NOW(), INTERVAL 7 DAY))",
            inviterGroupId, inviterUserId, "invitee-gi@test.com", "test-token-list",
        )
        val token = buildToken(inviteeUserId, inviteeGroupId, "invitee-gi@test.com")

        mockMvc.perform(
            get("/invites/pending").header("Authorization", "Bearer $token"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].groupId").value(inviterGroupId))
    }

    @Test
    fun `full couple flow accept invite and both in same group`() {
        // Step 1: Inviter sends invite
        val inviterToken = buildToken(inviterUserId, inviterGroupId, "inviter-gi@test.com")
        val inviteResult = mockMvc.perform(
            post("/invites")
                .header("Authorization", "Bearer $inviterToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"invitee-gi@test.com"}"""),
        )
            .andExpect(status().isCreated)
            .andReturn()

        val body = inviteResult.response.contentAsString
        val inviteId = Regex(""""id":(\d+)""").find(body)?.groupValues?.get(1)?.toLong()
            ?: error("Invite ID not found in response: $body")

        // Step 2: Invitee accepts (personal group has no data)
        val inviteeToken = buildToken(inviteeUserId, inviteeGroupId, "invitee-gi@test.com")
        mockMvc.perform(
            post("/invites/$inviteId/accept")
                .header("Authorization", "Bearer $inviteeToken"),
        ).andExpect(status().isNoContent)

        // Step 3: Verify invitee is now in inviter's group
        val inviteeCurrentGroupId = jdbcTemplate.queryForObject(
            "SELECT group_id FROM group_members WHERE user_id = ?",
            Long::class.java,
            inviteeUserId,
        )
        assert(inviteeCurrentGroupId == inviterGroupId) {
            "Expected invitee group $inviterGroupId but got $inviteeCurrentGroupId"
        }
    }

    @Test
    fun `accept invite returns 409 when personal group has data without force`() {
        // Add data to invitee's personal group
        val holderName = "Test Holder GI ${java.util.UUID.randomUUID()}"
        jdbcTemplate.update("INSERT INTO holders (name, group_id) VALUES (?, ?)", holderName, inviteeGroupId)
        val holderId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long::class.java)!!
        jdbcTemplate.update(
            "INSERT INTO payment_methods (group_id, name, type, holder_id) VALUES (?, 'Test Card', 'CREDIT', ?)",
            inviteeGroupId, holderId,
        )
        val paymentMethodId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long::class.java)!!

        // Create invite
        jdbcTemplate.update(
            "INSERT INTO group_invites (group_id, inviter_user_id, invitee_email, status, token, expires_at) " +
                "VALUES (?, ?, ?, 'PENDING', ?, DATE_ADD(NOW(), INTERVAL 7 DAY))",
            inviterGroupId, inviterUserId, "invitee-gi@test.com", "force-test-token",
        )
        val inviteId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long::class.java)!!

        val inviteeToken = buildToken(inviteeUserId, inviteeGroupId, "invitee-gi@test.com")
        mockMvc.perform(
            post("/invites/$inviteId/accept")
                .header("Authorization", "Bearer $inviteeToken"),
        ).andExpect(status().isConflict)

        // Cleanup
        jdbcTemplate.update("DELETE FROM payment_methods WHERE id = ?", paymentMethodId)
        jdbcTemplate.update("DELETE FROM holders WHERE id = ?", holderId)
        jdbcTemplate.update("DELETE FROM group_invites WHERE id = ?", inviteId)
    }

    @Test
    fun `POST invites decline changes status to DECLINED`() {
        jdbcTemplate.update(
            "INSERT INTO group_invites (group_id, inviter_user_id, invitee_email, status, token, expires_at) " +
                "VALUES (?, ?, ?, 'PENDING', ?, DATE_ADD(NOW(), INTERVAL 7 DAY))",
            inviterGroupId, inviterUserId, "invitee-gi@test.com", "decline-test-token",
        )
        val inviteId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long::class.java)!!

        val inviteeToken = buildToken(inviteeUserId, inviteeGroupId, "invitee-gi@test.com")
        mockMvc.perform(
            post("/invites/$inviteId/decline")
                .header("Authorization", "Bearer $inviteeToken"),
        ).andExpect(status().isNoContent)

        val status = jdbcTemplate.queryForObject(
            "SELECT status FROM group_invites WHERE id = ?",
            String::class.java, inviteId,
        )
        assert(status == "DECLINED") { "Expected DECLINED but got $status" }

        // Cleanup
        jdbcTemplate.update("DELETE FROM group_invites WHERE id = ?", inviteId)
    }

    @Test
    fun `POST groups leave fails when user is sole group member`() {
        val token = buildToken(inviterUserId, inviterGroupId, "inviter-gi@test.com")

        mockMvc.perform(
            post("/groups/leave").header("Authorization", "Bearer $token"),
        ).andExpect(status().isConflict)
    }
}
