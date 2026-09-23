package br.com.nomar.controlai.application.account_deletion

import br.com.nomar.controlai.config.TestDatabaseCleaner
import br.com.nomar.controlai.domain.auth.TokenHasher
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
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

// AccountDeletionGuardFilter over the real security chain: tokens come from /auth/login and the
// deletion is scheduled through POST /me/deletion, as the app does.
@SpringBootTest
@AutoConfigureMockMvc
class AccountDeletionGuardIntegrationTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var jdbcTemplate: JdbcTemplate
    @Autowired private lateinit var objectMapper: ObjectMapper
    @Autowired private lateinit var testDatabaseCleaner: TestDatabaseCleaner

    private val userEmail = "deletion-guard-user@controlai.test"
    private val partnerEmail = "deletion-guard-partner@controlai.test"
    private val emails = listOf(userEmail, partnerEmail)
    private val password = "senha-segura-123"
    private val rawApiKey = "cap_deletionguardtestkey1234567890"

    private var groupId = 0L

    @BeforeEach
    fun setUp() {
        cleanUp()
        jdbcTemplate.update("INSERT INTO `groups` (name) VALUES ('DeletionGuardGroup')")
        groupId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long::class.java)!!
        insertUser("Deletion Guard User", userEmail)
    }

    @AfterEach
    fun cleanUp() {
        // The ingestion through the API key stores a notification in the group
        testDatabaseCleaner.deleteFinancialData()
        val userIds = emails.flatMap { email ->
            jdbcTemplate.queryForList("SELECT id FROM users WHERE email = ?", Long::class.java, email)
        }
        val groupIds = userIds.flatMap { id ->
            jdbcTemplate.queryForList("SELECT group_id FROM group_members WHERE user_id = ?", Long::class.java, id)
        }.distinct()
        userIds.forEach { id ->
            jdbcTemplate.update("DELETE FROM refresh_tokens WHERE user_id = ?", id)
            jdbcTemplate.update("DELETE FROM group_members WHERE user_id = ?", id)
        }
        groupIds.forEach { id ->
            jdbcTemplate.update("DELETE FROM api_keys WHERE group_id = ?", id)
            jdbcTemplate.update("DELETE FROM subscriptions WHERE group_id = ?", id)
            jdbcTemplate.update("DELETE FROM `groups` WHERE id = ?", id)
        }
        userIds.forEach { id -> jdbcTemplate.update("DELETE FROM users WHERE id = ?", id) }
    }

    @Test
    fun `users without a scheduled deletion notice no difference`() {
        activateSubscription()
        val token = login(userEmail)

        mockMvc.perform(authorized(get("/purchases"), token))
            .andExpect(status().isOk)
    }

    @Test
    fun `protected routes answer 423 with the deletion date while scheduled`() {
        activateSubscription()
        val token = login(userEmail)
        val scheduledFor = requestDeletion(token)

        listOf(get("/purchases"), get("/payment-methods"), get("/categories")).forEach { request ->
            mockMvc.perform(authorized(request, token))
                .andExpect(status().`is`(423))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value("account_deletion_scheduled"))
                .andExpect(jsonPath("$.scheduledFor").value(scheduledFor))
        }
    }

    @Test
    fun `login, health and the deletion routes stay available while scheduled`() {
        activateSubscription()
        requestDeletion(login(userEmail))

        // Login keeps issuing tokens, so the user can see and cancel the deletion
        val token = login(userEmail)
        mockMvc.perform(get("/health")).andExpect(status().isOk)
        mockMvc.perform(authorized(get("/me/deletion"), token))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("SCHEDULED"))
        // Asking again is a conflict, not a lock
        mockMvc.perform(authorized(post("/me/deletion"), token))
            .andExpect(status().isConflict)
    }

    @Test
    fun `cancelling the deletion restores access`() {
        activateSubscription()
        val token = login(userEmail)
        requestDeletion(token)
        mockMvc.perform(authorized(get("/purchases"), token)).andExpect(status().`is`(423))

        mockMvc.perform(authorized(delete("/me/deletion"), token))
            .andExpect(status().isNoContent)

        mockMvc.perform(authorized(get("/purchases"), token))
            .andExpect(status().isOk)
    }

    @Test
    fun `423 takes priority over 402 for a group without subscription`() {
        val token = login(userEmail)
        mockMvc.perform(authorized(get("/purchases"), token)).andExpect(status().`is`(402))

        requestDeletion(token)

        mockMvc.perform(authorized(get("/purchases"), token))
            .andExpect(status().`is`(423))
            .andExpect(jsonPath("$.error").value("account_deletion_scheduled"))
    }

    @Test
    fun `API key of a group whose sole member asked for deletion answers 423`() {
        activateSubscription()
        createApiKey()
        requestDeletion(login(userEmail))

        postNotification()
            .andExpect(status().`is`(423))
            .andExpect(jsonPath("$.error").value("account_deletion_scheduled"))
    }

    @Test
    fun `API key keeps ingesting while a partner in the group stays`() {
        activateSubscription()
        createApiKey()
        insertUser("Deletion Guard Partner", partnerEmail)
        requestDeletion(login(userEmail))

        postNotification().andExpect(status().isCreated)
    }

    @Test
    fun `API key answers 423 once the partner also asked for deletion`() {
        activateSubscription()
        createApiKey()
        insertUser("Deletion Guard Partner", partnerEmail)
        requestDeletion(login(userEmail))
        requestDeletion(login(partnerEmail))

        postNotification().andExpect(status().`is`(423))
    }

    private fun requestDeletion(token: String): String {
        val body = mockMvc.perform(authorized(post("/me/deletion"), token))
            .andExpect(status().isAccepted)
            .andReturn().response.contentAsString
        return objectMapper.readTree(body)["scheduledFor"].asText()
    }

    private fun postNotification() = mockMvc.perform(
        post("/payments/notification")
            .header("X-Api-Key", rawApiKey)
            // Drops the default test bearer, so only the API key authenticates the request
            .header(HttpHeaders.AUTHORIZATION, "")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"text": "Compra aprovada no cartao final 4321 em 01/01/2026 10:00. Valor de R$ 10,00 em 1x Padaria.", "origin": "sms-app"}"""),
    )

    private fun login(email: String): String {
        val body = mockMvc.perform(
            post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$email","password":"$password"}"""),
        ).andExpect(status().isOk).andReturn().response.contentAsString
        return objectMapper.readTree(body)["accessToken"].asText()
    }

    // Overrides the default test bearer (user 1) with the logged-in user's own token
    private fun authorized(request: MockHttpServletRequestBuilder, accessToken: String) =
        request.header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")

    private fun insertUser(name: String, email: String) {
        jdbcTemplate.update(
            "INSERT INTO users (name, email, password_hash) VALUES (?, ?, ?)",
            name,
            email,
            BCryptPasswordEncoder().encode(password),
        )
        val id = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long::class.java)!!
        jdbcTemplate.update("INSERT INTO group_members (group_id, user_id) VALUES (?, ?)", groupId, id)
    }

    private fun activateSubscription() {
        jdbcTemplate.update("INSERT INTO subscriptions (group_id, plan, status) VALUES (?, 'ANNUAL', 'ACTIVE')", groupId)
    }

    private fun createApiKey() {
        jdbcTemplate.update(
            "INSERT INTO api_keys (group_id, key_hash, label) VALUES (?, ?, 'Deletion Guard Test Key')",
            groupId,
            TokenHasher.sha256(rawApiKey),
        )
    }
}
