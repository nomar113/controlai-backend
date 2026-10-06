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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDate
import java.time.ZoneOffset

@SpringBootTest
@AutoConfigureMockMvc
class PendingInvoiceRulesIntegrationTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var jdbcTemplate: JdbcTemplate
    @Autowired private lateinit var databaseCleaner: TestDatabaseCleaner

    @BeforeEach
    fun cleanUp() = databaseCleaner.deleteFinancialData()

    @AfterEach
    fun cleanUpAfter() = databaseCleaner.deleteFinancialData()

    private fun registerPending(): ResultActions = mockMvc.perform(
        post("/purchases/invoices/pending")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"qrCodeContent": "$QR_CONTENT"}""")
    )

    private fun pendingInvoiceId(): Long {
        registerPending().andExpect(status().isCreated)
        return jdbcTemplate.queryForObject(
            "SELECT id FROM purchase_invoices WHERE access_key = ? AND deleted_at IS NULL", Long::class.java, ACCESS_KEY,
        )!!
    }

    private fun insertProcessedInvoice(merchantName: String = "Mercado Processado"): Long {
        jdbcTemplate.update(
            """INSERT INTO purchase_invoices
               (date, merchant_name, merchant_address, cnpj, invoice_url, access_key, subtotal, total, taxes, discount, group_id)
               VALUES (UTC_TIMESTAMP(), ?, '', '', '', NULL, 80, 80, 0, 0, 1)""",
            merchantName,
        )
        return jdbcTemplate.queryForObject(
            "SELECT id FROM purchase_invoices WHERE merchant_name = ?", Long::class.java, merchantName,
        )!!
    }

    private fun insertNotification(): Long {
        jdbcTemplate.update(
            """INSERT INTO payment_notifications
               (purchased_at, amount, merchant_name, number_of_installments, origin, origin_type, group_id)
               VALUES (UTC_TIMESTAMP(), 80, 'Notif Pendente', 1, 'MANUAL', 'MANUAL', 1)""",
        )
        return jdbcTemplate.queryForObject(
            "SELECT id FROM payment_notifications WHERE merchant_name = 'Notif Pendente'", Long::class.java,
        )!!
    }

    // Range around today so the test does not depend on the JVM default month at a month boundary
    private fun listInvoices(): ResultActions {
        val today = LocalDate.now(ZoneOffset.UTC)
        return mockMvc.perform(
            get("/purchases/invoices")
                .param("startDate", today.minusDays(1).toString())
                .param("endDate", today.plusDays(1).toString())
        )
    }

    @Test
    fun `GET invoices should return status for a mixed list with null total for the pending one`() {
        val pendingId = pendingInvoiceId()
        val processedId = insertProcessedInvoice()

        listInvoices()
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalElements").value(2))
            .andExpect(jsonPath("$.content[?(@.id == $pendingId)].status").value("PENDING"))
            .andExpect(jsonPath("$.content[?(@.id == $pendingId)].total").value(null as Any?))
            .andExpect(jsonPath("$.content[?(@.id == $pendingId)].merchantName").value(null as Any?))
            .andExpect(jsonPath("$.content[?(@.id == $processedId)].status").value("PROCESSED"))
            .andExpect(jsonPath("$.content[?(@.id == $processedId)].total").value(80.0))
    }

    @Test
    fun `GET purchases should mark payment notifications as PROCESSED and invoices with their own status`() {
        val pendingId = pendingInvoiceId()
        val notificationId = insertNotification()

        mockMvc.perform(get("/purchases"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[?(@.id == $pendingId && @.merchantName == null)].status").value("PENDING"))
            .andExpect(jsonPath("$[?(@.id == $notificationId && @.merchantName == 'Notif Pendente')].status").value("PROCESSED"))
    }

    @Test
    fun `GET invoice detail of a pending invoice should return only the captured data`() {
        val pendingId = pendingInvoiceId()

        mockMvc.perform(get("/purchases/invoices/$pendingId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("PENDING"))
            .andExpect(jsonPath("$.accessKey").value(ACCESS_KEY))
            .andExpect(jsonPath("$.invoiceUrl").value(QR_CONTENT))
            .andExpect(jsonPath("$.date").exists())
            .andExpect(jsonPath("$.total").value(null as Any?))
            .andExpect(jsonPath("$.merchantName").value(null as Any?))
            .andExpect(jsonPath("$.items").isEmpty)
            .andExpect(jsonPath("$.payments").isEmpty)
    }

    @Test
    fun `GET invoice detail of a processed invoice should return PROCESSED status`() {
        val processedId = insertProcessedInvoice()

        mockMvc.perform(get("/purchases/invoices/$processedId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("PROCESSED"))
    }

    @Test
    fun `cancel, associate, disassociate and both suggestions endpoints should return 409 for a pending invoice`() {
        val pendingId = pendingInvoiceId()
        val notificationId = insertNotification()

        mockMvc.perform(patch("/purchases/invoices/$pendingId/cancel"))
            .andExpect(status().isConflict)
        mockMvc.perform(
            patch("/purchases/invoices/$pendingId/associate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"paymentNotificationId": $notificationId}""")
        ).andExpect(status().isConflict)
        mockMvc.perform(delete("/purchases/invoices/$pendingId/associate"))
            .andExpect(status().isConflict)
        mockMvc.perform(get("/purchases/invoices/$pendingId/suggestions/search"))
            .andExpect(status().isConflict)
        mockMvc.perform(get("/purchases/invoices/$pendingId/suggestions"))
            .andExpect(status().isConflict)
        // Same association, started from the notification side
        mockMvc.perform(
            patch("/payments/notifications/$notificationId/associate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"purchaseInvoiceId": $pendingId}""")
        ).andExpect(status().isConflict)

        assertNull(jdbcTemplate.queryForObject(
            "SELECT cancelled_at FROM purchase_invoices WHERE id = ?", Any::class.java, pendingId,
        ))
        assertNull(jdbcTemplate.queryForObject(
            "SELECT purchase_invoice_id FROM payment_notifications WHERE id = ?", Any::class.java, notificationId,
        ))
    }

    @Test
    fun `cancel, associate and suggestions should keep working for a processed invoice`() {
        val processedId = insertProcessedInvoice()
        val notificationId = insertNotification()

        mockMvc.perform(get("/purchases/invoices/$processedId/suggestions/search"))
            .andExpect(status().isOk)
        mockMvc.perform(
            patch("/purchases/invoices/$processedId/associate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"paymentNotificationId": $notificationId}""")
        ).andExpect(status().isOk)
        mockMvc.perform(delete("/purchases/invoices/$processedId/associate"))
            .andExpect(status().isNoContent)
        mockMvc.perform(patch("/purchases/invoices/$processedId/cancel"))
            .andExpect(status().isOk)
    }

    @Test
    fun `deleting a pending invoice should free its access key for a new registration`() {
        val pendingId = pendingInvoiceId()

        mockMvc.perform(delete("/purchases/invoices/$pendingId"))
            .andExpect(status().isNoContent)

        registerPending()
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.status").value("PENDING"))

        assertEquals(1, jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM purchase_invoices WHERE access_key = ? AND deleted_at IS NULL", Int::class.java, ACCESS_KEY,
        ))
    }

    private companion object {
        const val ACCESS_KEY = "33260253358724000682650010000901721678115882"
        const val QR_CONTENT = "http://www4.fazenda.rj.gov.br/consultaNFCe/QRCode?p=$ACCESS_KEY|2|1|1|c77e3a5c4f7a9ad7d25fee080cac222faac1219d"
    }
}
