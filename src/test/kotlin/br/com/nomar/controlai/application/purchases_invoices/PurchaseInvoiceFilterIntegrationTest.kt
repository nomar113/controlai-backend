package br.com.nomar.controlai.application.purchases_invoices

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
class PurchaseInvoiceFilterIntegrationTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach
    fun cleanUp() {
        jdbcTemplate.update("DELETE FROM purchase_payments")
        jdbcTemplate.update("DELETE FROM purchase_items")
        jdbcTemplate.update("DELETE FROM purchase_invoices")
    }

    @Test
    fun `GET invoices with month param returns only invoices from that month`() {
        insertInvoice("2026-05-10 14:00:00", "Store May 1", 100.00)
        insertInvoice("2026-05-20 10:00:00", "Store May 2", 200.00)
        insertInvoice("2026-04-15 09:00:00", "Store April", 50.00)

        mockMvc.perform(get("/purchases/invoices").param("month", "2026-05"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content.length()").value(2))
            .andExpect(jsonPath("$.content[0].merchantName").value("Store May 2"))
            .andExpect(jsonPath("$.content[1].merchantName").value("Store May 1"))
            .andExpect(jsonPath("$.totalElements").value(2))
    }

    @Test
    fun `GET invoices with startDate and endDate returns invoices in range`() {
        insertInvoice("2026-01-10 10:00:00", "Store Jan", 100.00)
        insertInvoice("2026-01-20 10:00:00", "Store Jan 2", 150.00)
        insertInvoice("2026-02-10 10:00:00", "Store Feb", 200.00)
        insertInvoice("2026-03-10 10:00:00", "Store Mar", 300.00)

        mockMvc.perform(
            get("/purchases/invoices")
                .param("startDate", "2026-01-15")
                .param("endDate", "2026-02-15")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content.length()").value(2))
            .andExpect(jsonPath("$.content[0].merchantName").value("Store Feb"))
            .andExpect(jsonPath("$.content[1].merchantName").value("Store Jan 2"))
    }

    @Test
    fun `GET invoices with startDate and endDate takes precedence over month`() {
        insertInvoice("2026-05-10 10:00:00", "Store May", 100.00)
        insertInvoice("2026-03-10 10:00:00", "Store Mar", 200.00)

        mockMvc.perform(
            get("/purchases/invoices")
                .param("month", "2026-05")
                .param("startDate", "2026-03-01")
                .param("endDate", "2026-03-31")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content.length()").value(1))
            .andExpect(jsonPath("$.content[0].merchantName").value("Store Mar"))
    }

    @Test
    fun `GET invoices without params returns current month invoices`() {
        val now = java.time.LocalDateTime.now()
        val currentTimestamp = now.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        val lastMonth = now.minusMonths(1).format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))

        insertInvoice(currentTimestamp, "Store Current", 100.00)
        insertInvoice(lastMonth, "Store Last Month", 50.00)

        mockMvc.perform(get("/purchases/invoices"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content.length()").value(1))
            .andExpect(jsonPath("$.content[0].merchantName").value("Store Current"))
    }

    @Test
    fun `GET invoices with startDate after endDate returns 400`() {
        mockMvc.perform(
            get("/purchases/invoices")
                .param("startDate", "2026-03-01")
                .param("endDate", "2026-01-01")
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `GET invoices with invalid month format returns 400`() {
        mockMvc.perform(get("/purchases/invoices").param("month", "invalid"))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `GET invoices with pagination returns correct page`() {
        for (i in 1..5) {
            insertInvoice("2026-05-${10 + i} 10:00:00", "Store $i", i * 10.0)
        }

        mockMvc.perform(
            get("/purchases/invoices")
                .param("month", "2026-05")
                .param("page", "0")
                .param("size", "2")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content.length()").value(2))
            .andExpect(jsonPath("$.totalElements").value(5))
            .andExpect(jsonPath("$.totalPages").value(3))
            .andExpect(jsonPath("$.number").value(0))
            .andExpect(jsonPath("$.size").value(2))
            .andExpect(jsonPath("$.last").value(false))
    }

    @Test
    fun `GET invoices respects soft delete`() {
        insertInvoice("2026-05-10 14:00:00", "Active Store", 100.00)
        insertDeletedInvoice("2026-05-15 14:00:00", "Deleted Store", 200.00)

        mockMvc.perform(get("/purchases/invoices").param("month", "2026-05"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content.length()").value(1))
            .andExpect(jsonPath("$.content[0].merchantName").value("Active Store"))
    }

    @Test
    fun `GET invoices are ordered by date DESC`() {
        insertInvoice("2026-05-01 08:00:00", "First", 10.00)
        insertInvoice("2026-05-15 12:00:00", "Middle", 20.00)
        insertInvoice("2026-05-28 18:00:00", "Last", 30.00)

        mockMvc.perform(get("/purchases/invoices").param("month", "2026-05"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content[0].merchantName").value("Last"))
            .andExpect(jsonPath("$.content[1].merchantName").value("Middle"))
            .andExpect(jsonPath("$.content[2].merchantName").value("First"))
    }

    // --- Helpers ---

    private fun insertInvoice(date: String, merchantName: String, total: Double) {
        jdbcTemplate.update(
            """INSERT INTO purchase_invoices
               (date, merchant_name, merchant_address, cnpj, invoice_url, access_key, subtotal, total, taxes, discount, group_id)
               VALUES (?, ?, '', '', '', ?, 0, ?, 0, 0, 1)""",
            date, merchantName, java.util.UUID.randomUUID().toString().take(44), total
        )
    }

    private fun insertDeletedInvoice(date: String, merchantName: String, total: Double) {
        jdbcTemplate.update(
            """INSERT INTO purchase_invoices
               (date, merchant_name, merchant_address, cnpj, invoice_url, access_key, subtotal, total, taxes, discount, deleted_at, group_id)
               VALUES (?, ?, '', '', '', ?, 0, ?, 0, 0, CURRENT_TIMESTAMP, 1)""",
            date, merchantName, java.util.UUID.randomUUID().toString().take(44), total
        )
    }
}
