package br.com.nomar.controlai.application.payments_notification

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
class PaymentNotificationControllerTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var jdbcTemplate: JdbcTemplate
    @Autowired private lateinit var objectMapper: ObjectMapper

    @BeforeEach
    fun cleanUp() {
        jdbcTemplate.update("DELETE FROM installments")
        jdbcTemplate.update("DELETE FROM payment_notifications")
        jdbcTemplate.update("DELETE FROM purchase_invoices")
        jdbcTemplate.update("DELETE FROM sub_cards")
        jdbcTemplate.update("DELETE FROM budget_payment_periods")
        jdbcTemplate.update("DELETE FROM budget_incomes")
        jdbcTemplate.update("DELETE FROM budget_items")
        jdbcTemplate.update("DELETE FROM budgets")
        jdbcTemplate.update("DELETE FROM payment_methods")
        jdbcTemplate.update("DELETE FROM holders")
    }

    @AfterEach
    fun tearDown() {
        // POST /notifications/manual with numberOfInstallments > 1 now auto-creates budgets via
        // EnsureFutureBudgetProvider, which links budget_payment_periods to this suite's
        // payment_methods; without this cleanup those rows outlive the test and break other
        // suites' payment_methods/categories cleanup by FK (see EnsureFutureBudgetProviderTest).
        jdbcTemplate.update("DELETE FROM budget_payment_periods")
        jdbcTemplate.update("DELETE FROM budget_incomes")
        jdbcTemplate.update("DELETE FROM budget_items")
        jdbcTemplate.update("DELETE FROM budgets")
    }

    private fun insertHolder(name: String = "Titular Teste"): Long {
        jdbcTemplate.update("INSERT INTO holders (name, group_id) VALUES ('$name', 1)")
        return jdbcTemplate.queryForObject("SELECT MAX(id) FROM holders", Long::class.java)!!
    }

    private fun insertPaymentMethod(
        holderId: Long,
        name: String = "Cartao Teste",
        type: String = "CREDIT",
        closingDay: Int? = null,
    ): Long {
        jdbcTemplate.update(
            "INSERT INTO payment_methods (name, type, holder_id, closing_day, group_id) VALUES ('$name', '$type', $holderId, $closingDay, 1)"
        )
        return jdbcTemplate.queryForObject("SELECT MAX(id) FROM payment_methods", Long::class.java)!!
    }

    private fun insertSubCard(
        paymentMethodId: Long,
        lastFourDigits: String = "5678",
        type: String = "FISICO",
    ): Long {
        jdbcTemplate.update(
            "INSERT INTO sub_cards (payment_method_id, last_four_digits, type) " +
                "VALUES ($paymentMethodId, '$lastFourDigits', '$type')"
        )
        return jdbcTemplate.queryForObject("SELECT MAX(id) FROM sub_cards", Long::class.java)!!
    }

    private fun insertCancelledNotification(amount: Double = 150.00): Long {
        jdbcTemplate.update(
            "INSERT INTO payment_notifications " +
                "(card_last_digits, purchased_at, amount, merchant_name, number_of_installments, origin, origin_type, cancelled_at, group_id) " +
                "VALUES ('1234', '2024-06-15 10:00:00', $amount, 'Loja Test', 1, 'NUBANK', 'HTTP_REQUEST', '2024-06-20 10:00:00', 1)"
        )
        return jdbcTemplate.queryForObject("SELECT MAX(id) FROM payment_notifications", Long::class.java)!!
    }

    private fun insertInvoice(
        total: Double = 150.00,
        merchantName: String = "Mercado Teste",
        cnpj: String = "12.345.678/0001-90",
        totalItems: Int = 5,
    ): Long {
        jdbcTemplate.update(
            "INSERT INTO purchase_invoices (date, merchant_name, cnpj, total_items, total, group_id) " +
                "VALUES ('2024-06-15 10:00:00', '$merchantName', '$cnpj', $totalItems, $total, 1)"
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
                "(card_last_digits, purchased_at, amount, merchant_name, number_of_installments, origin, origin_type, purchase_invoice_id, group_id) " +
                "VALUES ('1234', '2024-06-15 10:00:00', $amount, 'Loja Test', 1, 'NUBANK', 'HTTP_REQUEST', $invoiceIdSql, 1)"
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

    // --- PATCH payment-method ---

    @Test
    fun `PATCH payment-method returns 200 when paymentMethodId is valid and subCardId is omitted`() {
        val holderId = insertHolder()
        val paymentMethodId = insertPaymentMethod(holderId)
        val notificationId = insertNotification()

        mockMvc.perform(
            patch("/payments/notifications/$notificationId/payment-method")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"paymentMethodId": $paymentMethodId}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(notificationId))
            .andExpect(jsonPath("$.paymentMethodId").value(paymentMethodId))
            .andExpect(jsonPath("$.subCardId").doesNotExist())
            .andExpect(jsonPath("$.cardLastDigits").value("1234"))
    }

    @Test
    fun `PATCH payment-method returns 200 when paymentMethodId and subCardId are valid`() {
        val holderId = insertHolder()
        val paymentMethodId = insertPaymentMethod(holderId)
        val subCardId = insertSubCard(paymentMethodId, lastFourDigits = "9876")
        val notificationId = insertNotification()

        mockMvc.perform(
            patch("/payments/notifications/$notificationId/payment-method")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"paymentMethodId": $paymentMethodId, "subCardId": $subCardId}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(notificationId))
            .andExpect(jsonPath("$.paymentMethodId").value(paymentMethodId))
            .andExpect(jsonPath("$.subCardId").value(subCardId))
            .andExpect(jsonPath("$.cardLastDigits").value("9876"))
    }

    @Test
    fun `PATCH payment-method returns 200 when re-confirming the same card (idempotent)`() {
        val holderId = insertHolder()
        val paymentMethodId = insertPaymentMethod(holderId)
        val subCardId = insertSubCard(paymentMethodId, lastFourDigits = "9876")
        val notificationId = insertNotification()
        val body = """{"paymentMethodId": $paymentMethodId, "subCardId": $subCardId}"""

        mockMvc.perform(
            patch("/payments/notifications/$notificationId/payment-method")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
        ).andExpect(status().isOk)

        mockMvc.perform(
            patch("/payments/notifications/$notificationId/payment-method")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.paymentMethodId").value(paymentMethodId))
            .andExpect(jsonPath("$.subCardId").value(subCardId))
            .andExpect(jsonPath("$.cardLastDigits").value("9876"))
    }

    @Test
    fun `PATCH payment-method returns 400 when paymentMethodId does not exist`() {
        val notificationId = insertNotification()

        mockMvc.perform(
            patch("/payments/notifications/$notificationId/payment-method")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"paymentMethodId": 999999}""")
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `PATCH payment-method returns 400 when subCardId does not belong to paymentMethod`() {
        val holderId = insertHolder()
        val paymentMethodId = insertPaymentMethod(holderId)
        val otherPaymentMethodId = insertPaymentMethod(holderId, name = "Outro Cartao")
        val foreignSubCardId = insertSubCard(otherPaymentMethodId, lastFourDigits = "4321")
        val notificationId = insertNotification()

        mockMvc.perform(
            patch("/payments/notifications/$notificationId/payment-method")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"paymentMethodId": $paymentMethodId, "subCardId": $foreignSubCardId}""")
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `PATCH payment-method returns 404 when notification does not exist`() {
        val holderId = insertHolder()
        val paymentMethodId = insertPaymentMethod(holderId)

        mockMvc.perform(
            patch("/payments/notifications/999999/payment-method")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"paymentMethodId": $paymentMethodId}""")
        )
            .andExpect(status().isNotFound)
    }

    @Test
    fun `PATCH payment-method returns 409 when notification is cancelled`() {
        val holderId = insertHolder()
        val paymentMethodId = insertPaymentMethod(holderId)
        val notificationId = insertCancelledNotification()

        mockMvc.perform(
            patch("/payments/notifications/$notificationId/payment-method")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"paymentMethodId": $paymentMethodId}""")
        )
            .andExpect(status().isConflict)
    }

    // --- POST notifications/manual ---

    private fun countInstallmentsByParent(parentId: Long): Int =
        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM installments WHERE parent_id = ?", Int::class.java, parentId)!!

    @Test
    fun `POST manual creates N installments automatically with the default split, without an installments override`() {
        val holderId = insertHolder()
        val paymentMethodId = insertPaymentMethod(holderId, type = "CREDIT_CARD", closingDay = 10)

        val result = mockMvc.perform(
            post("/payments/notifications/manual")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{
                        "merchantName": "Apple Store",
                        "amount": 300.00,
                        "purchasedAt": "2026-01-15T10:00:00",
                        "paymentMethodId": $paymentMethodId,
                        "numberOfInstallments": 3
                    }"""
                )
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.installments.length()").value(3))
            .andReturn()

        val notificationId = objectMapper.readTree(result.response.contentAsString).get("id").asLong()
        assertEquals(3, countInstallmentsByParent(notificationId))
    }

    @Test
    fun `POST manual with installments override adjusts the already-created installments instead of creating a second set`() {
        val holderId = insertHolder()
        val paymentMethodId = insertPaymentMethod(holderId, type = "CREDIT_CARD", closingDay = 10)

        val result = mockMvc.perform(
            post("/payments/notifications/manual")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{
                        "merchantName": "Apple Store",
                        "amount": 300.00,
                        "purchasedAt": "2026-01-15T10:00:00",
                        "paymentMethodId": $paymentMethodId,
                        "numberOfInstallments": 3,
                        "installments": [
                            {"installmentNumber": 1, "amount": 100.00},
                            {"installmentNumber": 2, "amount": 100.00},
                            {"installmentNumber": 3, "amount": 100.00}
                        ]
                    }"""
                )
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.installments.length()").value(3))
            .andExpect(jsonPath("$.installments[0].amount").value(100.00))
            .andExpect(jsonPath("$.installments[1].amount").value(100.00))
            .andExpect(jsonPath("$.installments[2].amount").value(100.00))
            .andReturn()

        val notificationId = objectMapper.readTree(result.response.contentAsString).get("id").asLong()
        assertEquals(3, countInstallmentsByParent(notificationId))
    }

    @Test
    fun `POST manual does not create installments for a cash purchase`() {
        val holderId = insertHolder()
        val paymentMethodId = insertPaymentMethod(holderId, type = "CREDIT_CARD", closingDay = 10)

        val result = mockMvc.perform(
            post("/payments/notifications/manual")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{
                        "merchantName": "Padaria",
                        "amount": 45.00,
                        "purchasedAt": "2026-01-15T10:00:00",
                        "paymentMethodId": $paymentMethodId
                    }"""
                )
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.installments.length()").value(0))
            .andReturn()

        val notificationId = objectMapper.readTree(result.response.contentAsString).get("id").asLong()
        assertEquals(0, countInstallmentsByParent(notificationId))
    }
}
