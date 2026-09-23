package br.com.nomar.controlai.application.account_deletion

import br.com.nomar.controlai.application.account_deletion.application.CancelAccountDeletionProvider
import br.com.nomar.controlai.application.account_deletion.application.ScheduleAccountDeletionProvider
import br.com.nomar.controlai.domain.account_deletion.entity.AccountDeletion
import br.com.nomar.controlai.domain.account_deletion.exception.AccountDeletionAlreadyScheduledException
import br.com.nomar.controlai.domain.account_deletion.exception.AccountDeletionNotScheduledException
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.sql.Timestamp
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

// End-to-end over the real HTTP stack: the user logs in with a password, so the access and
// refresh tokens are real ones issued by /auth/login (not the default test bearer of user 1).
@SpringBootTest
@AutoConfigureMockMvc
class AccountDeletionIntegrationTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var jdbcTemplate: JdbcTemplate
    @Autowired private lateinit var objectMapper: ObjectMapper
    @Autowired private lateinit var scheduleAccountDeletionProvider: ScheduleAccountDeletionProvider
    @Autowired private lateinit var cancelAccountDeletionProvider: CancelAccountDeletionProvider

    private val userEmail = "account-deletion-test@controlai.test"
    private val partnerEmail = "account-deletion-partner@controlai.test"
    private val password = "senha-segura-123"

    private var userId = 0L
    private var groupId = 0L
    private var partnerId = 0L
    private var partnerGroupId = 0L

    @BeforeEach
    fun setUp() {
        cleanUp()
        val passwordHash = BCryptPasswordEncoder().encode(password)
        groupId = insertGroup("AccountDeletionGroup")
        userId = insertUser("Deletion User", userEmail, passwordHash, groupId)
        // No subscription on the user's group: deletion must work without one
        partnerGroupId = insertGroup("AccountDeletionPartnerGroup")
        partnerId = insertUser("Partner User", partnerEmail, passwordHash, partnerGroupId)
    }

    @AfterEach
    fun cleanUp() {
        val userIds = jdbcTemplate.queryForList(
            "SELECT id FROM users WHERE email IN (?, ?)",
            Long::class.java,
            userEmail,
            partnerEmail,
        )
        userIds.forEach { id ->
            jdbcTemplate.update("DELETE FROM group_invites WHERE inviter_user_id = ?", id)
            jdbcTemplate.update("DELETE FROM refresh_tokens WHERE user_id = ?", id)
        }
        jdbcTemplate.update("DELETE FROM group_invites WHERE invitee_email IN (?, ?)", userEmail, partnerEmail)
        val groupIds = userIds.flatMap { id ->
            jdbcTemplate.queryForList("SELECT group_id FROM group_members WHERE user_id = ?", Long::class.java, id)
        }
        userIds.forEach { id -> jdbcTemplate.update("DELETE FROM group_members WHERE user_id = ?", id) }
        groupIds.forEach { id ->
            jdbcTemplate.update("DELETE FROM `groups` WHERE id = ?", id)
        }
        userIds.forEach { id -> jdbcTemplate.update("DELETE FROM users WHERE id = ?", id) }
    }

    @Test
    fun `V43 adds the deletion columns and index to users`() {
        val columns = jdbcTemplate.queryForList(
            """SELECT column_name FROM information_schema.columns
               WHERE table_schema = DATABASE() AND table_name = 'users'
                 AND column_name IN ('deletion_requested_at', 'deletion_scheduled_for')""",
            String::class.java,
        )
        assertEquals(setOf("deletion_requested_at", "deletion_scheduled_for"), columns.map { it.lowercase() }.toSet())

        val indexes = jdbcTemplate.queryForList(
            """SELECT DISTINCT index_name FROM information_schema.statistics
               WHERE table_schema = DATABASE() AND table_name = 'users'
                 AND index_name = 'idx_users_deletion_scheduled_for'""",
            String::class.java,
        )
        assertEquals(1, indexes.size)
    }

    @Test
    fun `GET reports NONE before the request and SCHEDULED after it`() {
        val session = login(userEmail)

        mockMvc.perform(authorized(get("/me/deletion"), session.accessToken))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("NONE"))
            .andExpect(jsonPath("$.scheduledFor").doesNotExist())

        mockMvc.perform(authorized(post("/me/deletion"), session.accessToken))
            .andExpect(status().isAccepted)

        mockMvc.perform(authorized(get("/me/deletion"), session.accessToken))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("SCHEDULED"))
            .andExpect(jsonPath("$.requestedAt").exists())
            .andExpect(jsonPath("$.scheduledFor").exists())
    }

    @Test
    fun `POST schedules the deletion 30 days ahead and revokes the refresh tokens`() {
        val session = login(userEmail)
        val before = Instant.now().truncatedTo(ChronoUnit.SECONDS)

        val body = mockMvc.perform(authorized(post("/me/deletion"), session.accessToken))
            .andExpect(status().isAccepted)
            .andReturn().response.contentAsString

        val scheduledFor = Instant.parse(objectMapper.readTree(body)["scheduledFor"].asText())
        val expectedMin = before.plus(Duration.ofDays(30))
        val expectedMax = Instant.now().plus(Duration.ofDays(30))
        assertTrue(scheduledFor in expectedMin..expectedMax, "unexpected scheduledFor $scheduledFor")

        val stored = jdbcTemplate.queryForMap(
            "SELECT deletion_requested_at, deletion_scheduled_for FROM users WHERE id = ?",
            userId,
        )
        assertNotNull(stored["deletion_requested_at"])
        assertEquals(scheduledFor, (stored["deletion_scheduled_for"] as Timestamp).toInstant())

        val activeTokens = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM refresh_tokens WHERE user_id = ? AND revoked_at IS NULL",
            Int::class.java,
            userId,
        )
        assertEquals(0, activeTokens)

        mockMvc.perform(
            post("/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"refreshToken":"${session.refreshToken}"}"""),
        ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `POST cancels pending invites sent and received by the user and keeps the others`() {
        val sentPending = insertInvite(groupId, userId, "someone-else@controlai.test", "PENDING")
        val sentAccepted = insertInvite(groupId, userId, "accepted@controlai.test", "ACCEPTED")
        val receivedPending = insertInvite(partnerGroupId, partnerId, userEmail, "PENDING")
        val unrelatedPending = insertInvite(partnerGroupId, partnerId, "third-party@controlai.test", "PENDING")
        val session = login(userEmail)

        mockMvc.perform(authorized(post("/me/deletion"), session.accessToken))
            .andExpect(status().isAccepted)

        assertEquals("CANCELLED", inviteStatus(sentPending))
        assertEquals("CANCELLED", inviteStatus(receivedPending))
        assertEquals("ACCEPTED", inviteStatus(sentAccepted))
        assertEquals("PENDING", inviteStatus(unrelatedPending))
    }

    @Test
    fun `POST again while scheduled returns 409 and keeps the original date`() {
        val session = login(userEmail)
        mockMvc.perform(authorized(post("/me/deletion"), session.accessToken))
            .andExpect(status().isAccepted)
        val original = scheduledForInDb()

        mockMvc.perform(authorized(post("/me/deletion"), session.accessToken))
            .andExpect(status().isConflict)

        assertEquals(original, scheduledForInDb())
    }

    @Test
    fun `DELETE clears the schedule with 204 and then returns 404`() {
        val session = login(userEmail)
        mockMvc.perform(authorized(post("/me/deletion"), session.accessToken))
            .andExpect(status().isAccepted)

        mockMvc.perform(authorized(delete("/me/deletion"), session.accessToken))
            .andExpect(status().isNoContent)

        val stored = jdbcTemplate.queryForMap(
            "SELECT deletion_requested_at, deletion_scheduled_for FROM users WHERE id = ?",
            userId,
        )
        assertNull(stored["deletion_requested_at"])
        assertNull(stored["deletion_scheduled_for"])

        mockMvc.perform(authorized(delete("/me/deletion"), session.accessToken))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `DELETE without a scheduled deletion returns 404`() {
        val session = login(userEmail)

        mockMvc.perform(authorized(delete("/me/deletion"), session.accessToken))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `requesting the deletion keeps every row of the user and the group`() {
        val session = login(userEmail)

        mockMvc.perform(authorized(post("/me/deletion"), session.accessToken))
            .andExpect(status().isAccepted)

        assertEquals(1, count("SELECT COUNT(*) FROM users WHERE id = ?", userId))
        assertEquals(1, count("SELECT COUNT(*) FROM group_members WHERE user_id = ?", userId))
        assertEquals(1, count("SELECT COUNT(*) FROM `groups` WHERE id = ?", groupId))
    }

    // The use case checks first; these cover the request that loses a race after that check
    @Test
    fun `schedule provider refuses an already scheduled account and rolls back the side effects`() {
        val now = Instant.now().truncatedTo(ChronoUnit.SECONDS)
        val first = AccountDeletion(requestedAt = now, scheduledFor = now.plus(Duration.ofDays(30)))
        scheduleAccountDeletionProvider.execute(userId, first).getOrThrow()
        login(userEmail)
        val pendingInvite = insertInvite(partnerGroupId, partnerId, userEmail, "PENDING")

        val second = AccountDeletion(requestedAt = now.plusSeconds(5), scheduledFor = now.plus(Duration.ofDays(31)))
        val result = scheduleAccountDeletionProvider.execute(userId, second)

        assertIs<AccountDeletionAlreadyScheduledException>(result.exceptionOrNull())
        assertEquals(first.scheduledFor, scheduledForInDb())
        assertEquals(1, count("SELECT COUNT(*) FROM refresh_tokens WHERE user_id = ? AND revoked_at IS NULL", userId))
        assertEquals("PENDING", inviteStatus(pendingInvite))
    }

    @Test
    fun `cancel provider refuses an account without a scheduled deletion`() {
        val result = cancelAccountDeletionProvider.execute(userId)

        assertIs<AccountDeletionNotScheduledException>(result.exceptionOrNull())
    }

    private data class Session(val accessToken: String, val refreshToken: String)

    private fun login(email: String): Session {
        val body = mockMvc.perform(
            post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$email","password":"$password"}"""),
        ).andExpect(status().isOk).andReturn().response.contentAsString
        val json = objectMapper.readTree(body)
        return Session(json["accessToken"].asText(), json["refreshToken"].asText())
    }

    // Overrides the default test bearer (user 1) with the logged-in user's own token
    private fun authorized(request: MockHttpServletRequestBuilder, accessToken: String) =
        request.header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")

    private fun insertGroup(name: String): Long {
        jdbcTemplate.update("INSERT INTO `groups` (name) VALUES (?)", name)
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long::class.java)!!
    }

    private fun insertUser(name: String, email: String, passwordHash: String, groupId: Long): Long {
        jdbcTemplate.update("INSERT INTO users (name, email, password_hash) VALUES (?, ?, ?)", name, email, passwordHash)
        val id = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long::class.java)!!
        jdbcTemplate.update("INSERT INTO group_members (group_id, user_id) VALUES (?, ?)", groupId, id)
        return id
    }

    private fun insertInvite(groupId: Long, inviterId: Long, inviteeEmail: String, status: String): Long {
        jdbcTemplate.update(
            """INSERT INTO group_invites (group_id, inviter_user_id, invitee_email, status, token, expires_at)
               VALUES (?, ?, ?, ?, ?, ?)""",
            groupId,
            inviterId,
            inviteeEmail,
            status,
            UUID.randomUUID().toString(),
            Timestamp.from(Instant.now().plus(Duration.ofDays(7))),
        )
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long::class.java)!!
    }

    private fun inviteStatus(id: Long): String =
        jdbcTemplate.queryForObject("SELECT status FROM group_invites WHERE id = ?", String::class.java, id)!!

    private fun scheduledForInDb(): Instant =
        jdbcTemplate.queryForObject(
            "SELECT deletion_scheduled_for FROM users WHERE id = ?",
            Timestamp::class.java,
            userId,
        )!!.toInstant()

    private fun count(sql: String, vararg args: Any): Int =
        jdbcTemplate.queryForObject(sql, Int::class.java, *args)!!
}
