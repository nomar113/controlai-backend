package br.com.nomar.controlai.application.purchases_invoices

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
class AssociateInvoiceControllerIntegrationTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach
    fun cleanUp() {
        clearTables()
    }

    @AfterEach
    fun cleanUpAfter() {
        clearTables()
    }

    private fun clearTables() {
        jdbcTemplate.update("DELETE FROM installments")
        jdbcTemplate.update("UPDATE payment_notifications SET category_id = NULL, payment_method_id = NULL, sub_card_id = NULL, purchase_invoice_id = NULL")
        jdbcTemplate.update("DELETE FROM payment_notifications")
        jdbcTemplate.update("DELETE FROM purchase_payments")
        jdbcTemplate.update("DELETE FROM purchase_items")
        jdbcTemplate.update("DELETE FROM purchase_invoices")
    }

    private fun insertInvoice(
        date: String = "2026-05-20 14:00:00",
        total: Double = 150.00,
        merchantName: String = "Mercado Teste ${UUID.randomUUID()}",
        cancelledAt: String? = null,
    ): Long {
        jdbcTemplate.update(
            """INSERT INTO purchase_invoices
               (date, merchant_name, merchant_address, cnpj, invoice_url, access_key, subtotal, total, taxes, discount, cancelled_at, group_id)
               VALUES (?, ?, '', '', '', ?, 0, ?, 0, 0, ?, 1)""",
            date,
            merchantName,
            UUID.randomUUID().toString().take(44),
            total,
            cancelledAt,
        )
        return jdbcTemplate.queryForObject(
            "SELECT id FROM purchase_invoices WHERE merchant_name = ? ORDER BY id DESC LIMIT 1",
            Long::class.java,
            merchantName,
        )!!
    }

    private fun insertNotification(
        purchasedAt: String = "2026-05-20 14:00:00",
        amount: Double = 150.00,
        merchantName: String = "Notif Teste ${UUID.randomUUID()}",
        purchaseInvoiceId: Long? = null,
        cancelledAt: String? = null,
        deletedAt: String? = null,
    ): Long {
        jdbcTemplate.update(
            """INSERT INTO payment_notifications
               (purchased_at, amount, merchant_name, number_of_installments, origin, origin_type, purchase_invoice_id, cancelled_at, deleted_at, group_id)
               VALUES (?, ?, ?, 1, 'MANUAL', 'MANUAL', ?, ?, ?, 1)""",
            purchasedAt,
            amount,
            merchantName,
            purchaseInvoiceId,
            cancelledAt,
            deletedAt,
        )
        return jdbcTemplate.queryForObject(
            "SELECT id FROM payment_notifications WHERE merchant_name = ? ORDER BY id DESC LIMIT 1",
            Long::class.java,
            merchantName,
        )!!
    }

    @Test
    fun `PATCH associate should return 200 with response body when successful`() {
        val invoiceId = insertInvoice()
        val notificationId = insertNotification()

        mockMvc.perform(
            patch("/purchases/invoices/$invoiceId/associate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"paymentNotificationId": $notificationId}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.invoiceId").value(invoiceId))
            .andExpect(jsonPath("$.paymentNotificationId").value(notificationId))
            .andExpect(jsonPath("$.associatedAt").exists())

        val storedInvoiceId = jdbcTemplate.queryForObject(
            "SELECT purchase_invoice_id FROM payment_notifications WHERE id = ?",
            Long::class.java,
            notificationId,
        )
        assert(storedInvoiceId == invoiceId)
    }

    @Test
    fun `PATCH associate should return 404 when invoice not found`() {
        val notificationId = insertNotification()

        mockMvc.perform(
            patch("/purchases/invoices/999999/associate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"paymentNotificationId": $notificationId}""")
        )
            .andExpect(status().isNotFound)
    }

    @Test
    fun `PATCH associate should return 404 when notification not found`() {
        val invoiceId = insertInvoice()

        mockMvc.perform(
            patch("/purchases/invoices/$invoiceId/associate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"paymentNotificationId": 999999}""")
        )
            .andExpect(status().isNotFound)
    }

    @Test
    fun `PATCH associate should return 409 when notification already associated to another invoice`() {
        val otherInvoiceId = insertInvoice(merchantName = "Other Invoice")
        val invoiceId = insertInvoice()
        val notificationId = insertNotification(purchaseInvoiceId = otherInvoiceId)

        mockMvc.perform(
            patch("/purchases/invoices/$invoiceId/associate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"paymentNotificationId": $notificationId}""")
        )
            .andExpect(status().isConflict)
    }

    @Test
    fun `DELETE associate should return 204 and clear the association`() {
        val invoiceId = insertInvoice()
        val notificationId = insertNotification(purchaseInvoiceId = invoiceId)

        mockMvc.perform(delete("/purchases/invoices/$invoiceId/associate"))
            .andExpect(status().isNoContent)

        val storedInvoiceId = jdbcTemplate.queryForObject(
            "SELECT purchase_invoice_id FROM payment_notifications WHERE id = ?",
            Long::class.java,
            notificationId,
        )
        assert(storedInvoiceId == null)
    }

    @Test
    fun `DELETE associate should return 204 when there is no association`() {
        val invoiceId = insertInvoice()

        mockMvc.perform(delete("/purchases/invoices/$invoiceId/associate"))
            .andExpect(status().isNoContent)
    }

    @Test
    fun `DELETE associate should return 404 when invoice does not exist`() {
        mockMvc.perform(delete("/purchases/invoices/999999/associate"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `GET search should return 200 with notifications matching amount filter`() {
        val invoiceId = insertInvoice()
        val targetId = insertNotification(amount = 250.00, merchantName = "Target Match")
        insertNotification(amount = 99.99, merchantName = "Wrong Amount")

        mockMvc.perform(
            get("/purchases/invoices/$invoiceId/suggestions/search")
                .param("amount", "250.00")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].id").value(targetId))
            .andExpect(jsonPath("$[0].amount").value(250.0))
    }

    @Test
    fun `GET search should return 200 with last 7 days when no filters provided`() {
        val invoiceId = insertInvoice()
        val recent = insertNotification(
            purchasedAt = java.time.LocalDateTime.now().minusDays(2).toString().replace("T", " "),
            merchantName = "Recent",
        )
        insertNotification(
            purchasedAt = java.time.LocalDateTime.now().minusDays(30).toString().replace("T", " "),
            merchantName = "Old",
        )

        mockMvc.perform(get("/purchases/invoices/$invoiceId/suggestions/search"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].id").value(recent))
    }

    @Test
    fun `GET search should return 400 when startDate has invalid format`() {
        val invoiceId = insertInvoice()

        mockMvc.perform(
            get("/purchases/invoices/$invoiceId/suggestions/search")
                .param("startDate", "not-a-date")
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `GET search should return 404 when invoice does not exist`() {
        mockMvc.perform(get("/purchases/invoices/999999/suggestions/search"))
            .andExpect(status().isNotFound)
    }
}
