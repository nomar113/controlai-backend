package br.com.nomar.controlai.application.purchases_invoices

import br.com.nomar.controlai.config.TestDatabaseCleaner
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

// Notice, "view" and retry endpoints of the app, over the real security chain and a real MySQL.
// Users 1 (the default token) and a second member of group 1 prove that "Nova" is per user.
@SpringBootTest
@AutoConfigureMockMvc
class InvoiceNoticeEndpointsIntegrationTest {

    @Autowired private lateinit var webApplicationContext: WebApplicationContext
    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var jdbcTemplate: JdbcTemplate
    @Autowired private lateinit var jwtEncoder: JwtEncoder
    @Autowired private lateinit var databaseCleaner: TestDatabaseCleaner

    private lateinit var anonymousMockMvc: MockMvc
    private var otherGroupId = 0L
    private var otherUserId = 0L

    @BeforeEach
    fun setUp() {
        anonymousMockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
            .apply<DefaultMockMvcBuilder>(springSecurity())
            .build()
        databaseCleaner.deleteFinancialData()
        jdbcTemplate.update("INSERT INTO `groups` (name) VALUES ('Notice Other Group')")
        otherGroupId = lastInsertId()
        jdbcTemplate.update("INSERT INTO users (name, email) VALUES ('Notice Second Member', 'notice-second@controlai.dev')")
        otherUserId = lastInsertId()
        // Both users saw the notice a while ago, so anything processed "now" is newer
        jdbcTemplate.update("UPDATE users SET invoice_notice_seen_at = DATE_SUB(UTC_TIMESTAMP(6), INTERVAL 1 HOUR) WHERE id IN (1, ?)", otherUserId)
    }

    @AfterEach
    fun tearDown() {
        databaseCleaner.deleteFinancialData()
        jdbcTemplate.update("DELETE FROM users WHERE id = ?", otherUserId)
        jdbcTemplate.update("DELETE FROM `groups` WHERE id = ?", otherGroupId)
    }

    private fun lastInsertId(): Long = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long::class.java)!!

    private var keySeq = 0

    private fun insertInvoice(
        status: String,
        groupId: Long = 1L,
        processedAtExpr: String = "NULL",
        lastFailure: String? = null,
        attempts: Int = 0,
    ): Long {
        val key = "3526" + (++keySeq).toString().padStart(40, '0')
        jdbcTemplate.update(
            """INSERT INTO purchase_invoices
               (date, invoice_url, access_key, status, processed_at, read_attempts, last_read_failure, group_id)
               VALUES (UTC_TIMESTAMP(), 'https://example.test/nfce', ?, ?, $processedAtExpr, ?, ?, ?)""",
            key, status, attempts, lastFailure, groupId,
        )
        return lastInsertId()
    }

    private fun processedNow(groupId: Long = 1L) = insertInvoice("PROCESSED", groupId, "UTC_TIMESTAMP(6)")

    private fun bearer(userId: Long, groupId: Long = 1L): String {
        val now = Instant.now()
        val claims = JwtClaimsSet.builder()
            .subject(userId.toString()).issuedAt(now).expiresAt(now.plus(Duration.ofHours(1)))
            .claim("groupId", groupId).claim("email", "notice@controlai.dev").build()
        return "Bearer " + jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).tokenValue
    }

    private fun MockHttpServletRequestBuilder.asUser(userId: Long) = header("Authorization", bearer(userId))

    private fun noticeCount(userId: Long = 1L): Int {
        val body = mockMvc.perform(get("/purchases/invoices/processed-notice").asUser(userId))
            .andExpect(status().isOk).andReturn().response.contentAsString
        return Regex("\"count\":(\\d+)").find(body)!!.groupValues[1].toInt()
    }

    private fun isNewInList(userId: Long, invoiceId: Long): Boolean {
        val today = LocalDate.now(ZoneOffset.UTC)
        val body = mockMvc.perform(
            get("/purchases/invoices").param("startDate", today.minusDays(1).toString())
                .param("endDate", today.plusDays(1).toString()).asUser(userId)
        ).andExpect(status().isOk).andReturn().response.contentAsString
        val node = com.fasterxml.jackson.databind.ObjectMapper().readTree(body)["content"].first { it["id"].asLong() == invoiceId }
        return node["isNew"].asBoolean()
    }

    private fun isNewInDetail(userId: Long, invoiceId: Long): Boolean {
        val body = mockMvc.perform(get("/purchases/invoices/$invoiceId").asUser(userId))
            .andExpect(status().isOk).andReturn().response.contentAsString
        return com.fasterxml.jackson.databind.ObjectMapper().readTree(body)["isNew"].asBoolean()
    }

    @Test
    fun `notice should count only PROCESSED invoices of the group processed after the last notice`() {
        processedNow()
        processedNow()
        processedNow(groupId = otherGroupId)
        insertInvoice("PENDING")
        insertInvoice("READ_FAILED", lastFailure = "TIMEOUT", attempts = 3)
        // processed before the user last saw the notice
        insertInvoice("PROCESSED", processedAtExpr = "DATE_SUB(UTC_TIMESTAMP(6), INTERVAL 2 HOUR)")
        // processed_at is null for invoices that were PROCESSED before V48
        insertInvoice("PROCESSED")
        // deleted
        val deleted = processedNow()
        jdbcTemplate.update("UPDATE purchase_invoices SET deleted_at = UTC_TIMESTAMP() WHERE id = ?", deleted)

        assertEquals(2, noticeCount())
    }

    @Test
    fun `dismiss should zero the count only for the caller`() {
        processedNow()
        assertEquals(1, noticeCount(1L))
        assertEquals(1, noticeCount(otherUserId))

        mockMvc.perform(post("/purchases/invoices/processed-notice/dismiss").asUser(1L))
            .andExpect(status().isNoContent)

        assertEquals(0, noticeCount(1L))
        assertEquals(1, noticeCount(otherUserId))
    }

    @Test
    fun `isNew should disappear after view or after dismissing the notice, per user`() {
        val viewedId = processedNow()
        val dismissedId = processedNow()

        assertEquals(true, isNewInList(1L, viewedId))
        assertEquals(true, isNewInDetail(1L, viewedId))

        mockMvc.perform(post("/purchases/invoices/$viewedId/view").asUser(1L)).andExpect(status().isNoContent)

        assertEquals(false, isNewInList(1L, viewedId))
        assertEquals(false, isNewInDetail(1L, viewedId))
        assertEquals(true, isNewInList(1L, dismissedId))
        // the other member still sees the badge
        assertEquals(true, isNewInList(otherUserId, viewedId))
        assertEquals(true, isNewInDetail(otherUserId, viewedId))

        mockMvc.perform(post("/purchases/invoices/processed-notice/dismiss").asUser(1L)).andExpect(status().isNoContent)

        assertEquals(false, isNewInList(1L, dismissedId))
        assertEquals(true, isNewInList(otherUserId, dismissedId))
    }

    @Test
    fun `view should be idempotent and 404 outside the group`() {
        val id = processedNow()
        val foreign = processedNow(groupId = otherGroupId)

        repeat(2) {
            mockMvc.perform(post("/purchases/invoices/$id/view").asUser(1L)).andExpect(status().isNoContent)
        }
        assertEquals(
            1,
            jdbcTemplate.queryForObject("SELECT COUNT(*) FROM invoice_views WHERE user_id = 1 AND purchase_invoice_id = ?", Int::class.java, id),
        )

        mockMvc.perform(post("/purchases/invoices/$foreign/view").asUser(1L)).andExpect(status().isNotFound)
        mockMvc.perform(post("/purchases/invoices/999999/view").asUser(1L)).andExpect(status().isNotFound)
        assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM invoice_views WHERE purchase_invoice_id = ?", Int::class.java, foreign))
    }

    @Test
    fun `retry should return READ_FAILED to PENDING with attempts and reason cleared`() {
        val id = insertInvoice("READ_FAILED", lastFailure = "TIMEOUT", attempts = 3)

        mockMvc.perform(get("/purchases/invoices/$id").asUser(1L))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("READ_FAILED"))
            .andExpect(jsonPath("$.readFailureReason").value("TIMEOUT"))

        mockMvc.perform(post("/purchases/invoices/$id/retry").asUser(1L))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("PENDING"))
            .andExpect(jsonPath("$.readFailureReason").doesNotExist())
            .andExpect(jsonPath("$.isNew").value(false))

        val row = jdbcTemplate.queryForMap("SELECT status, read_attempts, last_read_failure FROM purchase_invoices WHERE id = ?", id)
        assertEquals("PENDING", row["status"])
        assertEquals(0, row["read_attempts"])
        assertEquals(null, row["last_read_failure"])
        // back in the processor queue
        anonymousMockMvc.perform(get("/internal/nfce-processor/invoices/pending"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items[?(@.id == $id)]").isNotEmpty)
    }

    @Test
    fun `retry should be 409 unless READ_FAILED and 404 outside the group`() {
        val pending = insertInvoice("PENDING", lastFailure = "TIMEOUT", attempts = 1)
        val processed = processedNow()
        val foreign = insertInvoice("READ_FAILED", groupId = otherGroupId, lastFailure = "TIMEOUT", attempts = 3)

        mockMvc.perform(post("/purchases/invoices/$pending/retry").asUser(1L)).andExpect(status().isConflict)
        mockMvc.perform(post("/purchases/invoices/$processed/retry").asUser(1L)).andExpect(status().isConflict)
        mockMvc.perform(post("/purchases/invoices/$foreign/retry").asUser(1L)).andExpect(status().isNotFound)

        assertEquals("READ_FAILED", jdbcTemplate.queryForObject("SELECT status FROM purchase_invoices WHERE id = ?", String::class.java, foreign))
        assertEquals(1, jdbcTemplate.queryForObject("SELECT read_attempts FROM purchase_invoices WHERE id = ?", Int::class.java, pending))
    }

    @Test
    fun `list and detail should carry readFailureReason only for READ_FAILED`() {
        val failed = insertInvoice("READ_FAILED", lastFailure = "NOT_FOUND", attempts = 3)
        val pendingWithHistory = insertInvoice("PENDING", lastFailure = "TIMEOUT", attempts = 1)
        val today = LocalDate.now(ZoneOffset.UTC)

        val body = mockMvc.perform(
            get("/purchases/invoices").param("startDate", today.minusDays(1).toString())
                .param("endDate", today.plusDays(1).toString()).asUser(1L)
        ).andExpect(status().isOk).andReturn().response.contentAsString
        val content = com.fasterxml.jackson.databind.ObjectMapper().readTree(body)["content"]

        assertEquals("NOT_FOUND", content.first { it["id"].asLong() == failed }["readFailureReason"].asText())
        assertEquals(true, content.first { it["id"].asLong() == pendingWithHistory }["readFailureReason"].isNull)
        mockMvc.perform(get("/purchases/invoices/$pendingWithHistory").asUser(1L))
            .andExpect(jsonPath("$.readFailureReason").doesNotExist())
    }

    @Test
    fun `endpoints should require a JWT`() {
        val id = processedNow()

        anonymousMockMvc.perform(get("/purchases/invoices/processed-notice")).andExpect(status().isUnauthorized)
        anonymousMockMvc.perform(post("/purchases/invoices/processed-notice/dismiss")).andExpect(status().isUnauthorized)
        anonymousMockMvc.perform(post("/purchases/invoices/$id/view")).andExpect(status().isUnauthorized)
        anonymousMockMvc.perform(post("/purchases/invoices/$id/retry")).andExpect(status().isUnauthorized)
    }
}
