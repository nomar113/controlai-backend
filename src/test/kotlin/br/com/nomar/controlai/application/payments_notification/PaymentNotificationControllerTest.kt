package br.com.nomar.controlai.application.payments_notification

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
class PaymentNotificationControllerTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach
    fun cleanUp() {
        jdbcTemplate.update("DELETE FROM installments")
        jdbcTemplate.update("DELETE FROM payment_notifications")
        jdbcTemplate.update("DELETE FROM purchase_invoices")
    }

    private fun insertInvoice(
        total: Double = 150.00,
        merchantName: String = "Mercado Teste",
        cnpj: String = "12.345.678/0001-90",
        totalItems: Int = 5,
    ): Long {
        jdbcTemplate.update(
            "INSERT INTO purchase_invoices (date, merchant_name, cnpj, total_items, total) " +
                "VALUES ('2024-06-15 10:00:00', '$merchantName', '$cnpj', $totalItems, $total)"
        )
        return jdbcTemplate.queryForObject("SELECT MAX(id) FROM purchase_invoices", Long::class.java)!!
    }

    private fun insertNotification(
        amount: Double = 150.00,
        purchaseInvoiceId: Long? = null,
    ): Long {
        val invoiceIdSql = if (purchaseInvoiceId != null) purchaseInvoiceId.toString() else "NULL"
        jdbcTemplate.update(
            "INSERT INTO payment_notifications " +
                "(card_last_digits, purchased_at, amount, merchant_name, number_of_installments, origin, origin_type, purchase_invoice_id) " +
                "VALUES ('1234', '2024-06-15 10:00:00', $amount, 'Loja Test', 1, 'NUBANK', 'HTTP_REQUEST', $invoiceIdSql)"
        )
        return jdbcTemplate.queryForObject("SELECT MAX(id) FROM payment_notifications", Long::class.java)!!
    }

    // --- GET invoice-suggestions ---

    @Test
    fun `GET invoice-suggestions returns 200 with empty list when no candidates exist`() {
        val id = insertNotification(amount = 99.90)

        mockMvc.perform(get("/payments/notifications/$id/invoice-suggestions"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(0))
    }

    @Test
    fun `GET invoice-suggestions returns 200 with candidates when invoice total matches notification amount`() {
        val id = insertNotification(amount = 150.00)
        insertInvoice(total = 150.00, merchantName = "Loja Sugestao", cnpj = "11.222.333/0001-44", totalItems = 3)

        mockMvc.perform(get("/payments/notifications/$id/invoice-suggestions"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].merchantName").value("Loja Sugestao"))
            .andExpect(jsonPath("$[0].cnpj").value("11.222.333/0001-44"))
            .andExpect(jsonPath("$[0].total").value(150.00))
            .andExpect(jsonPath("$[0].totalItems").value(3))
            .andExpect(jsonPath("$[0].id").isNumber)
            .andExpect(jsonPath("$[0].date").exists())
    }

    @Test
    fun `GET invoice-suggestions returns 404 for non-existent notification`() {
        mockMvc.perform(get("/payments/notifications/999999/invoice-suggestions"))
            .andExpect(status().isNotFound)
    }

    // --- PATCH associate ---

    @Test
    fun `PATCH associate returns 200 with associatedInvoice populated on success`() {
        val invoiceId = insertInvoice(total = 150.00, merchantName = "Mercado Teste")
        val notificationId = insertNotification(amount = 150.00)

        mockMvc.perform(
            patch("/payments/notifications/$notificationId/associate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"purchaseInvoiceId": $invoiceId}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.purchaseInvoiceId").value(invoiceId))
            .andExpect(jsonPath("$.associatedInvoice").exists())
            .andExpect(jsonPath("$.associatedInvoice.id").value(invoiceId))
            .andExpect(jsonPath("$.associatedInvoice.merchantName").value("Mercado Teste"))
            .andExpect(jsonPath("$.associatedInvoice.total").value(150.00))
    }

    @Test
    fun `PATCH associate returns 404 for non-existent notification`() {
        val invoiceId = insertInvoice()

        mockMvc.perform(
            patch("/payments/notifications/999999/associate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"purchaseInvoiceId": $invoiceId}""")
        )
            .andExpect(status().isNotFound)
    }

    @Test
    fun `PATCH associate returns 404 for non-existent invoice`() {
        val notificationId = insertNotification()

        mockMvc.perform(
            patch("/payments/notifications/$notificationId/associate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"purchaseInvoiceId": 999999}""")
        )
            .andExpect(status().isNotFound)
    }

    @Test
    fun `PATCH associate returns 409 when notification is already associated`() {
        val existingInvoiceId = insertInvoice(total = 150.00)
        val notificationId = insertNotification(amount = 150.00, purchaseInvoiceId = existingInvoiceId)
        val anotherInvoiceId = insertInvoice(total = 200.00)

        mockMvc.perform(
            patch("/payments/notifications/$notificationId/associate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"purchaseInvoiceId": $anotherInvoiceId}""")
        )
            .andExpect(status().isConflict)
    }
}
