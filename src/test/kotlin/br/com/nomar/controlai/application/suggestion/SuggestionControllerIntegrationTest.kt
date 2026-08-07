package br.com.nomar.controlai.application.suggestion

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
class SuggestionControllerIntegrationTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var jdbcTemplate: JdbcTemplate

    private var invoiceId: Long = 0

    @BeforeEach
    fun cleanUp() {
        jdbcTemplate.update("DELETE FROM installments")
        jdbcTemplate.update("UPDATE payment_notifications SET category_id = NULL, payment_method_id = NULL, sub_card_id = NULL")
        jdbcTemplate.update("DELETE FROM payment_notifications")
        jdbcTemplate.update("DELETE FROM purchase_payments")
        jdbcTemplate.update("DELETE FROM purchase_items")
        jdbcTemplate.update("DELETE FROM purchase_invoices")
    }

    private fun insertInvoice(date: String, total: Double, merchantName: String = "Test Store"): Long {
        jdbcTemplate.update(
            """INSERT INTO purchase_invoices
               (date, merchant_name, merchant_address, cnpj, invoice_url, access_key, subtotal, total, taxes, discount, group_id)
               VALUES (?, ?, '', '', '', ?, 0, ?, 0, 0, 1)""",
            date, merchantName, java.util.UUID.randomUUID().toString().take(44), total
        )
        return jdbcTemplate.queryForObject(
            "SELECT id FROM purchase_invoices WHERE merchant_name = ? ORDER BY id DESC LIMIT 1",
            Long::class.java, merchantName
        )!!
    }

    private fun insertNotification(
        purchasedAt: String,
        amount: Double,
        merchantName: String = "Test Store",
        cancelledAt: String? = null,
        deletedAt: String? = null,
    ): Long {
        jdbcTemplate.update(
            """INSERT INTO payment_notifications
               (purchased_at, amount, merchant_name, number_of_installments, origin, origin_type, cancelled_at, deleted_at, group_id)
               VALUES (?, ?, ?, 1, 'MANUAL', 'MANUAL', ?, ?, 1)""",
            purchasedAt, amount, merchantName, cancelledAt, deletedAt
        )
        return jdbcTemplate.queryForObject(
            "SELECT id FROM payment_notifications WHERE merchant_name = ? ORDER BY id DESC LIMIT 1",
            Long::class.java, merchantName
        )!!
    }

    @Test
    fun `match exato - valor igual retorna sugestao independente da data`() {
        invoiceId = insertInvoice("2026-05-20 14:00:00", 150.00)
        insertNotification("2026-01-10 09:00:00", 150.00, "Match Antigo")

        mockMvc.perform(get("/purchases/invoices/$invoiceId/suggestions"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].amount").value(150.0))
            .andExpect(jsonPath("$[0].merchantName").value("Match Antigo"))
    }

    @Test
    fun `sem match por valor diferente retorna lista vazia`() {
        invoiceId = insertInvoice("2026-05-20 14:00:00", 150.00)
        insertNotification("2026-05-20 14:30:00", 200.00, "Different Amount")

        mockMvc.perform(get("/purchases/invoices/$invoiceId/suggestions"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(0))
    }

    @Test
    fun `notification cancelada excluida dos resultados`() {
        invoiceId = insertInvoice("2026-05-20 14:00:00", 150.00)
        insertNotification(
            "2026-05-20 14:10:00", 150.00, "Cancelled Notif",
            cancelledAt = "2026-05-21 10:00:00"
        )

        mockMvc.perform(get("/purchases/invoices/$invoiceId/suggestions"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(0))
    }

    @Test
    fun `notification deletada excluida dos resultados`() {
        invoiceId = insertInvoice("2026-05-20 14:00:00", 150.00)
        insertNotification(
            "2026-05-20 14:10:00", 150.00, "Deleted Notif",
            deletedAt = "2026-05-21 10:00:00"
        )

        mockMvc.perform(get("/purchases/invoices/$invoiceId/suggestions"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(0))
    }

    @Test
    fun `ordenacao por purchased_at DESC - mais recente primeiro`() {
        invoiceId = insertInvoice("2026-05-20 14:00:00", 100.00)
        insertNotification("2026-05-18 10:00:00", 100.00, "Antigo")
        insertNotification("2026-05-20 14:50:00", 100.00, "Recente")
        insertNotification("2026-05-19 12:00:00", 100.00, "Meio")

        mockMvc.perform(get("/purchases/invoices/$invoiceId/suggestions"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(3))
            .andExpect(jsonPath("$[0].merchantName").value("Recente"))
            .andExpect(jsonPath("$[1].merchantName").value("Meio"))
            .andExpect(jsonPath("$[2].merchantName").value("Antigo"))
    }

    @Test
    fun `invoice inexistente retorna 404`() {
        mockMvc.perform(get("/purchases/invoices/999999/suggestions"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `match exato mas sem notifications candidatas retorna lista vazia 200`() {
        invoiceId = insertInvoice("2026-05-20 14:00:00", 250.00)

        mockMvc.perform(get("/purchases/invoices/$invoiceId/suggestions"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(0))
    }
}
