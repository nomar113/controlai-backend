package br.com.nomar.controlai.application.nfce_processor

import br.com.nomar.controlai.config.TestDatabaseCleaner
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
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
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
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
import java.time.LocalDateTime

// The NFC-e processor endpoints over the real security chain. They are open, so every request goes
// through a raw MockMvc without the default Bearer token of TestAuthMockMvcConfig.
@SpringBootTest
@AutoConfigureMockMvc
class NfceProcessorEndpointsIntegrationTest {

    @Autowired private lateinit var webApplicationContext: WebApplicationContext
    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var jdbcTemplate: JdbcTemplate
    @Autowired private lateinit var jwtEncoder: JwtEncoder
    @Autowired private lateinit var databaseCleaner: TestDatabaseCleaner

    private lateinit var anonymousMockMvc: MockMvc
    private var otherGroupId = 0L
    private var lockedUserId = 0L

    @BeforeEach
    fun setUp() {
        anonymousMockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
            .apply<DefaultMockMvcBuilder>(springSecurity())
            .build()
        databaseCleaner.deleteFinancialData()
        // A group without subscription: its token gets 402 on any guarded route
        jdbcTemplate.update("INSERT INTO `groups` (name) VALUES ('NFC-e Processor Other Group')")
        otherGroupId = lastInsertId()
        // A user with a scheduled deletion: its token gets 423 on any guarded route
        jdbcTemplate.update(
            "INSERT INTO users (name, email, deletion_scheduled_for) VALUES (?, ?, DATE_ADD(UTC_TIMESTAMP(), INTERVAL 30 DAY))",
            "NFC-e Processor Locked", LOCKED_EMAIL,
        )
        lockedUserId = lastInsertId()
    }

    @AfterEach
    fun tearDown() {
        databaseCleaner.deleteFinancialData()
        jdbcTemplate.update("DELETE FROM users WHERE id = ?", lockedUserId)
        jdbcTemplate.update("DELETE FROM `groups` WHERE id = ?", otherGroupId)
    }

    private fun lastInsertId(): Long = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long::class.java)!!

    private fun insertInvoice(
        accessKey: String,
        createdAt: LocalDateTime,
        status: String = "PENDING",
        groupId: Long = 1L,
        deleted: Boolean = false,
    ): Long {
        jdbcTemplate.update(
            """INSERT INTO purchase_invoices
               (date, invoice_url, access_key, status, group_id, created_at, deleted_at)
               VALUES (?, ?, ?, ?, ?, ?, ?)""",
            createdAt, "$SEFAZ_URL$accessKey|2|1|1|hash", accessKey, status, groupId, createdAt,
            if (deleted) createdAt else null,
        )
        return lastInsertId()
    }

    private fun listPending(limit: Int? = null): ResultActions {
        val request = get("/internal/nfce-processor/invoices/pending")
        limit?.let { request.param("limit", it.toString()) }
        return anonymousMockMvc.perform(request)
    }

    private fun failure(id: Long, accessKey: String, reason: String = "TIMEOUT", mvc: MockMvc = anonymousMockMvc): ResultActions =
        mvc.perform(failureRequest(id, accessKey, reason))

    private fun failureRequest(id: Long, accessKey: String, reason: String): MockHttpServletRequestBuilder =
        post("/internal/nfce-processor/invoices/$id/failure")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"accessKey": "$accessKey", "reason": "$reason"}""")

    private fun readState(id: Long): Map<String, Any?> = jdbcTemplate.queryForMap(
        "SELECT status, read_attempts, last_read_failure FROM purchase_invoices WHERE id = ?", id,
    )

    private fun token(userId: Long, groupId: Long): String {
        val now = Instant.now()
        val claims = JwtClaimsSet.builder()
            .subject(userId.toString())
            .issuedAt(now)
            .expiresAt(now.plus(Duration.ofHours(1)))
            .claim("groupId", groupId)
            .claim("email", "nfce-processor-test@controlai.dev")
            .build()
        return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).tokenValue
    }

    @Test
    fun `pending should list only PENDING not deleted invoices of all groups, oldest first`() {
        val base = LocalDateTime.of(2026, 10, 1, 12, 0)
        val newest = insertInvoice(key(1), base.plusHours(2))
        val oldest = insertInvoice(key(2), base, groupId = otherGroupId)
        val middle = insertInvoice(key(3), base.plusHours(1))
        insertInvoice(key(4), base.minusHours(1), deleted = true)
        insertInvoice(key(5), base.minusHours(2), status = "PROCESSED")
        insertInvoice(key(6), base.minusHours(3), status = "READ_FAILED")

        listPending()
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items.length()").value(3))
            .andExpect(jsonPath("$.items[0].id").value(oldest))
            .andExpect(jsonPath("$.items[1].id").value(middle))
            .andExpect(jsonPath("$.items[2].id").value(newest))
            .andExpect(jsonPath("$.items[0].accessKey").value(key(2)))
            .andExpect(jsonPath("$.items[0].invoiceUrl").value("$SEFAZ_URL${key(2)}|2|1|1|hash"))
            .andExpect(jsonPath("$.items[0].scannedAt").value("2026-10-01T12:00:00Z"))
            .andExpect(jsonPath("$.remaining").value(0))
    }

    @Test
    fun `pending should respect the limit and count the remaining invoices`() {
        val base = LocalDateTime.of(2026, 10, 1, 12, 0)
        val first = insertInvoice(key(1), base)
        val second = insertInvoice(key(2), base.plusMinutes(1))
        insertInvoice(key(3), base.plusMinutes(2))

        listPending(limit = 2)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items.length()").value(2))
            .andExpect(jsonPath("$.items[0].id").value(first))
            .andExpect(jsonPath("$.items[1].id").value(second))
            .andExpect(jsonPath("$.remaining").value(1))
    }

    @Test
    fun `pending should answer 400 for a limit out of 1 to 200`() {
        listPending(limit = 0).andExpect(status().isBadRequest)
        listPending(limit = 201).andExpect(status().isBadRequest)
        listPending(limit = 200).andExpect(status().isOk)
    }

    @Test
    fun `pending items should carry only id, invoiceUrl, accessKey and scannedAt`() {
        insertInvoice(key(1), LocalDateTime.of(2026, 10, 1, 12, 0))

        val body = listPending().andExpect(status().isOk).andReturn().response.contentAsString

        assertEquals(
            """{"items":[{"id":%d,"invoiceUrl":"%s","accessKey":"%s","scannedAt":"2026-10-01T12:00:00Z"}],"remaining":0}"""
                .format(
                    jdbcTemplate.queryForObject("SELECT id FROM purchase_invoices WHERE access_key = ?", Long::class.java, key(1)),
                    "$SEFAZ_URL${key(1)}|2|1|1|hash",
                    key(1),
                ),
            body,
        )
        assertFalse(body.contains("group", ignoreCase = true))
        assertFalse(body.contains("user", ignoreCase = true))
    }

    @Test
    fun `the 3rd failure should move the invoice to READ_FAILED with the last reason`() {
        val id = insertInvoice(key(1), LocalDateTime.of(2026, 10, 1, 12, 0))

        failure(id, key(1), "TIMEOUT")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.outcome").value("RETRY_SCHEDULED"))
            .andExpect(jsonPath("$.attempts").value(1))
        failure(id, key(1), "NOT_FOUND")
            .andExpect(jsonPath("$.outcome").value("RETRY_SCHEDULED"))
            .andExpect(jsonPath("$.attempts").value(2))
        assertEquals(mapOf("status" to "PENDING", "read_attempts" to 2, "last_read_failure" to "NOT_FOUND"), readState(id))

        failure(id, key(1), "UNEXPECTED_LAYOUT")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.outcome").value("READ_FAILED"))
            .andExpect(jsonPath("$.attempts").value(3))

        assertEquals(mapOf("status" to "READ_FAILED", "read_attempts" to 3, "last_read_failure" to "UNEXPECTED_LAYOUT"), readState(id))
        // Out of the queue from now on
        listPending().andExpect(jsonPath("$.items.length()").value(0))
        // A 4th failure finds no PENDING invoice
        failure(id, key(1)).andExpect(jsonPath("$.outcome").value("DISCARDED"))
        assertEquals(3, readState(id)["read_attempts"])
    }

    @Test
    fun `a failure for a deleted, not pending or mismatched invoice should be DISCARDED without changes`() {
        val created = LocalDateTime.of(2026, 10, 1, 12, 0)
        val deleted = insertInvoice(key(1), created, deleted = true)
        val processed = insertInvoice(key(2), created, status = "PROCESSED")
        val pending = insertInvoice(key(3), created)

        listOf(deleted to key(1), processed to key(2), pending to key(4), 999_999L to key(5)).forEach { (id, accessKey) ->
            failure(id, accessKey)
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.outcome").value("DISCARDED"))
                .andExpect(jsonPath("$.attempts").value(0))
        }

        listOf(deleted to "PENDING", processed to "PROCESSED", pending to "PENDING").forEach { (id, status) ->
            assertEquals(mapOf("status" to status, "read_attempts" to 0, "last_read_failure" to null), readState(id))
        }
    }

    @Test
    fun `failure should answer 400 for an unknown reason or an invalid access key`() {
        val id = insertInvoice(key(1), LocalDateTime.of(2026, 10, 1, 12, 0))

        failure(id, key(1), reason = "BLOCKED").andExpect(status().isBadRequest)
        failure(id, "123").andExpect(status().isBadRequest)

        assertEquals(0, readState(id)["read_attempts"])
    }

    @Test
    fun `the endpoints should skip the subscription and deletion guards`() {
        val id = insertInvoice(key(1), LocalDateTime.of(2026, 10, 1, 12, 0))
        val unpaidToken = token(userId = 1L, groupId = otherGroupId)
        val lockedToken = token(userId = lockedUserId, groupId = 1L)
        // Control: the same tokens are rejected by the guards on an app route
        mockMvc.perform(get("/purchases").header("Authorization", "Bearer $unpaidToken")).andExpect(status().`is`(402))
        mockMvc.perform(get("/purchases").header("Authorization", "Bearer $lockedToken")).andExpect(status().`is`(423))

        listOf(unpaidToken, lockedToken).forEach { token ->
            mockMvc.perform(get("/internal/nfce-processor/invoices/pending").header("Authorization", "Bearer $token"))
                .andExpect(status().isOk)
        }
        mockMvc.perform(failureRequest(id, key(1), "TIMEOUT").header("Authorization", "Bearer $unpaidToken"))
            .andExpect(status().isOk)
        mockMvc.perform(failureRequest(id, key(1), "TIMEOUT").header("Authorization", "Bearer $lockedToken"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.attempts").value(2))
    }

    private companion object {
        const val SEFAZ_URL = "http://www4.fazenda.rj.gov.br/consultaNFCe/QRCode?p="
        const val LOCKED_EMAIL = "nfce-processor-locked@controlai.test"

        // Distinct 44-digit access keys
        fun key(n: Int) = "332602533587240006826500100009017216781158" + n.toString().padStart(2, '0')
    }
}
