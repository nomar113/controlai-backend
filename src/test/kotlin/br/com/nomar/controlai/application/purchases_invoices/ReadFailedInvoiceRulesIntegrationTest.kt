package br.com.nomar.controlai.application.purchases_invoices

import br.com.nomar.controlai.config.TestDatabaseCleaner
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

// A READ_FAILED invoice is not a purchase: every purchase action answers 409 and no total counts it.
// The processor flow that leads to READ_FAILED arrives later, so the rows are inserted with SQL.
@SpringBootTest
@AutoConfigureMockMvc
class ReadFailedInvoiceRulesIntegrationTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var jdbcTemplate: JdbcTemplate
    @Autowired private lateinit var databaseCleaner: TestDatabaseCleaner

    @BeforeEach
    fun cleanUp() = databaseCleaner.deleteFinancialData()

    @AfterEach
    fun cleanUpAfter() = databaseCleaner.deleteFinancialData()

    // total stays null, like the processor leaves it; a total can be forced to prove the status filter alone excludes it
    private fun insertReadFailedInvoice(total: BigDecimal? = null): Long {
        jdbcTemplate.update(
            """INSERT INTO purchase_invoices
               (date, invoice_url, access_key, total, status, read_attempts, last_read_failure, group_id)
               VALUES (UTC_TIMESTAMP(), ?, ?, ?, 'READ_FAILED', 3, 'TIMEOUT', 1)""",
            QR_CONTENT, ACCESS_KEY, total,
        )
        return lastInsertId()
    }

    private fun insertProcessedInvoice(): Long {
        jdbcTemplate.update(
            """INSERT INTO purchase_invoices
               (date, merchant_name, merchant_address, cnpj, invoice_url, access_key, subtotal, total, taxes, discount, group_id)
               VALUES (UTC_TIMESTAMP(), 'Mercado Processado', '', '', '', NULL, 80, 80, 0, 0, 1)""",
        )
        return lastInsertId()
    }

    private fun insertNotification(): Long {
        jdbcTemplate.update(
            """INSERT INTO payment_notifications
               (purchased_at, amount, merchant_name, number_of_installments, origin, origin_type, group_id)
               VALUES (UTC_TIMESTAMP(), 80, 'Notif Falha Leitura', 1, 'MANUAL', 'MANUAL', 1)""",
        )
        return lastInsertId()
    }

    private fun lastInsertId(): Long = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long::class.java)!!

    // Range around today so the test does not depend on the JVM default month at a month boundary
    private fun listInvoices(): ResultActions {
        val today = LocalDate.now(ZoneOffset.UTC)
        return mockMvc.perform(
            get("/purchases/invoices")
                .param("startDate", today.minusDays(1).toString())
                .param("endDate", today.plusDays(1).toString())
        )
    }

    private fun paymentMethodsSummary(): String = mockMvc.perform(
        get("/payment-methods/summary").param("month", YearMonth.now(ZoneOffset.UTC).toString())
    ).andExpect(status().isOk).andReturn().response.contentAsString

    @Test
    fun `cancel, associate, disassociate and both suggestions endpoints should return 409 for a READ_FAILED invoice`() {
        val readFailedId = insertReadFailedInvoice()
        val notificationId = insertNotification()

        mockMvc.perform(patch("/purchases/invoices/$readFailedId/cancel"))
            .andExpect(status().isConflict)
        mockMvc.perform(
            patch("/purchases/invoices/$readFailedId/associate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"paymentNotificationId": $notificationId}""")
        ).andExpect(status().isConflict)
        mockMvc.perform(delete("/purchases/invoices/$readFailedId/associate"))
            .andExpect(status().isConflict)
        mockMvc.perform(get("/purchases/invoices/$readFailedId/suggestions/search"))
            .andExpect(status().isConflict)
        mockMvc.perform(get("/purchases/invoices/$readFailedId/suggestions"))
            .andExpect(status().isConflict)
        // Same association, started from the notification side
        mockMvc.perform(
            patch("/payments/notifications/$notificationId/associate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"purchaseInvoiceId": $readFailedId}""")
        ).andExpect(status().isConflict)

        assertNull(jdbcTemplate.queryForObject(
            "SELECT cancelled_at FROM purchase_invoices WHERE id = ?", Any::class.java, readFailedId,
        ))
        assertNull(jdbcTemplate.queryForObject(
            "SELECT purchase_invoice_id FROM payment_notifications WHERE id = ?", Any::class.java, notificationId,
        ))
    }

    @Test
    fun `GET invoices and detail should return the READ_FAILED status without values`() {
        val readFailedId = insertReadFailedInvoice()
        val processedId = insertProcessedInvoice()

        listInvoices()
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalElements").value(2))
            .andExpect(jsonPath("$.content[?(@.id == $readFailedId)].status").value("READ_FAILED"))
            .andExpect(jsonPath("$.content[?(@.id == $readFailedId)].total").value(null as Any?))
            .andExpect(jsonPath("$.content[?(@.id == $processedId)].total").value(80.0))

        mockMvc.perform(get("/purchases/invoices/$readFailedId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("READ_FAILED"))
            .andExpect(jsonPath("$.accessKey").value(ACCESS_KEY))
            .andExpect(jsonPath("$.total").value(null as Any?))
    }

    @Test
    fun `totals should not change with a READ_FAILED invoice`() {
        insertProcessedInvoice()
        insertNotification()
        val summaryBefore = paymentMethodsSummary()

        // A forced total: a sum filtering the wrong status would count it, while a null would hide the bug
        insertReadFailedInvoice(total = BigDecimal("80.00"))

        assertEquals(summaryBefore, paymentMethodsSummary())
    }

    @Test
    fun `invoice suggestions of a notification should never offer a READ_FAILED invoice, even with a matching total`() {
        val readFailedId = insertReadFailedInvoice(total = BigDecimal("80.00"))
        val notificationId = insertNotification()

        mockMvc.perform(get("/payments/notifications/$notificationId/invoice-suggestions"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[?(@.id == $readFailedId)]").isEmpty)
    }

    @Test
    fun `deleting a READ_FAILED invoice should be allowed`() {
        val readFailedId = insertReadFailedInvoice()

        mockMvc.perform(delete("/purchases/invoices/$readFailedId"))
            .andExpect(status().isNoContent)
    }

    private companion object {
        const val ACCESS_KEY = "33260253358724000682650010000901721678115882"
        const val QR_CONTENT = "http://www4.fazenda.rj.gov.br/consultaNFCe/QRCode?p=$ACCESS_KEY|2|1|1|c77e3a5c4f7a9ad7d25fee080cac222faac1219d"
    }
}
