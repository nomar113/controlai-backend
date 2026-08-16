package br.com.nomar.controlai.application.payments_notification

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
import kotlin.test.assertEquals

@SpringBootTest
@AutoConfigureMockMvc
class PaymentNotificationFilterIntegrationTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var jdbcTemplate: JdbcTemplate
    private var paymentMethodId: Long = 0

    @BeforeEach
    fun cleanUp() {
        jdbcTemplate.update("DELETE FROM installments")
        jdbcTemplate.update("DELETE FROM payment_notifications")
        jdbcTemplate.update("DELETE FROM budget_payment_periods")
        jdbcTemplate.update("DELETE FROM budgets")
        jdbcTemplate.update("DELETE FROM sub_cards")
        jdbcTemplate.update("DELETE FROM payment_methods")
        jdbcTemplate.update("DELETE FROM holders")

        val holderId = createHolder()
        paymentMethodId = createPaymentMethod(holderId)
    }

    @Test
    fun `GET notifications with month param returns only notifications from that month`() {
        insertNotification("2026-05-10 14:00:00", "Store May 1", 100.00)
        insertNotification("2026-05-20 10:00:00", "Store May 2", 200.00)
        insertNotification("2026-04-15 09:00:00", "Store April", 50.00)
        insertNotification("2026-06-01 08:00:00", "Store June", 75.00)

        mockMvc.perform(get("/payments/notifications").param("month", "2026-05"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content.length()").value(2))
            .andExpect(jsonPath("$.content[0].merchantName").value("Store May 2"))
            .andExpect(jsonPath("$.content[1].merchantName").value("Store May 1"))
            .andExpect(jsonPath("$.totalElements").value(2))
    }

    @Test
    fun `GET notifications without month param returns current month notifications`() {
        val now = java.time.LocalDateTime.now()
        val currentTimestamp = now.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        val lastMonth = now.minusMonths(1).format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))

        insertNotification(currentTimestamp, "Store Current", 100.00)
        insertNotification(lastMonth, "Store Last Month", 50.00)

        mockMvc.perform(get("/payments/notifications"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content.length()").value(1))
            .andExpect(jsonPath("$.content[0].merchantName").value("Store Current"))
    }

    @Test
    fun `GET notifications with invalid month format returns 400`() {
        mockMvc.perform(get("/payments/notifications").param("month", "invalid"))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `GET notifications with pagination returns correct page`() {
        for (i in 1..5) {
            insertNotification("2026-05-${10 + i} 10:00:00", "Store $i", i * 10.0)
        }

        mockMvc.perform(
            get("/payments/notifications")
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

        mockMvc.perform(
            get("/payments/notifications")
                .param("month", "2026-05")
                .param("page", "2")
                .param("size", "2")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content.length()").value(1))
            .andExpect(jsonPath("$.last").value(true))
    }

    @Test
    fun `GET notifications for a month without an existing budget lists purchases without creating one`() {
        insertNotification("2026-09-10 14:00:00", "Store September", 100.00)

        mockMvc.perform(get("/payments/notifications").param("month", "2026-09"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content.length()").value(1))
            .andExpect(jsonPath("$.content[0].merchantName").value("Store September"))

        val budgetCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM budgets WHERE reference_month = ?", Long::class.java, "2026-09"
        )
        assertEquals(0L, budgetCount)
    }

    @Test
    fun `GET notifications for month with no data returns empty page`() {
        insertNotification("2026-05-10 14:00:00", "Store May", 100.00)

        mockMvc.perform(get("/payments/notifications").param("month", "2026-01"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content.length()").value(0))
            .andExpect(jsonPath("$.totalElements").value(0))
    }

    @Test
    fun `GET notifications respects soft delete`() {
        insertNotification("2026-05-10 14:00:00", "Active Store", 100.00)
        insertDeletedNotification("2026-05-15 14:00:00", "Deleted Store", 200.00)

        mockMvc.perform(get("/payments/notifications").param("month", "2026-05"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content.length()").value(1))
            .andExpect(jsonPath("$.content[0].merchantName").value("Active Store"))
    }

    @Test
    fun `GET notifications ignores notifications without payment method`() {
        insertNotification("2026-05-10 14:00:00", "With Payment Method", 100.00)
        insertNotificationWithoutPaymentMethod("2026-05-15 14:00:00", "Without Payment Method", 200.00)

        mockMvc.perform(get("/payments/notifications").param("month", "2026-05"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content.length()").value(1))
            .andExpect(jsonPath("$.content[0].merchantName").value("With Payment Method"))
            .andExpect(jsonPath("$.totalElements").value(1))
    }

    @Test
    fun `GET notifications are ordered by purchasedAt DESC`() {
        insertNotification("2026-05-01 08:00:00", "First", 10.00)
        insertNotification("2026-05-15 12:00:00", "Middle", 20.00)
        insertNotification("2026-05-28 18:00:00", "Last", 30.00)

        mockMvc.perform(get("/payments/notifications").param("month", "2026-05"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content[0].merchantName").value("Last"))
            .andExpect(jsonPath("$.content[1].merchantName").value("Middle"))
            .andExpect(jsonPath("$.content[2].merchantName").value("First"))
    }

    // --- Filtro por paymentMethodId ---

    @Test
    fun `GET notifications with paymentMethodId returns only notifications of that payment method`() {
        val holderId = jdbcTemplate.queryForObject("SELECT id FROM holders LIMIT 1", Long::class.java)!!
        val otherPaymentMethodId = createPaymentMethod(holderId, "Other Card")
        insertNotification("2026-05-10 14:00:00", "Main Card Store", 100.00)
        insertNotification("2026-05-12 10:00:00", "Other Card Store", 200.00, otherPaymentMethodId)

        mockMvc.perform(
            get("/payments/notifications")
                .param("month", "2026-05")
                .param("paymentMethodId", paymentMethodId.toString())
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content.length()").value(1))
            .andExpect(jsonPath("$.content[0].merchantName").value("Main Card Store"))
            .andExpect(jsonPath("$.totalElements").value(1))
    }

    @Test
    fun `GET notifications with paymentMethodId includes purchases made with sub-cards of that method`() {
        val subCardId = createSubCard(paymentMethodId)
        insertNotification("2026-05-10 14:00:00", "Parent Card Store", 100.00)
        insertNotification("2026-05-12 10:00:00", "Sub Card Store", 200.00, paymentMethodId, subCardId)

        mockMvc.perform(
            get("/payments/notifications")
                .param("month", "2026-05")
                .param("paymentMethodId", paymentMethodId.toString())
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content.length()").value(2))
            .andExpect(jsonPath("$.content[0].merchantName").value("Sub Card Store"))
            .andExpect(jsonPath("$.content[1].merchantName").value("Parent Card Store"))
            .andExpect(jsonPath("$.totalElements").value(2))
    }

    @Test
    fun `GET notifications with paymentMethodId without matches returns empty page`() {
        val holderId = jdbcTemplate.queryForObject("SELECT id FROM holders LIMIT 1", Long::class.java)!!
        val otherPaymentMethodId = createPaymentMethod(holderId, "Other Card")
        insertNotification("2026-05-10 14:00:00", "Main Card Store", 100.00)

        mockMvc.perform(
            get("/payments/notifications")
                .param("month", "2026-05")
                .param("paymentMethodId", otherPaymentMethodId.toString())
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content.length()").value(0))
            .andExpect(jsonPath("$.totalElements").value(0))
    }

    // --- Ordenacao (sort) ---

    @Test
    fun `GET notifications with sort=amount orders by amount DESC`() {
        insertNotification("2026-05-28 18:00:00", "Cheapest", 10.00)
        insertNotification("2026-05-01 08:00:00", "Most Expensive", 300.00)
        insertNotification("2026-05-15 12:00:00", "Middle Price", 150.00)

        mockMvc.perform(
            get("/payments/notifications")
                .param("month", "2026-05")
                .param("sort", "amount")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content[0].merchantName").value("Most Expensive"))
            .andExpect(jsonPath("$.content[1].merchantName").value("Middle Price"))
            .andExpect(jsonPath("$.content[2].merchantName").value("Cheapest"))
    }

    @Test
    fun `GET notifications with sort=recent orders by purchasedAt DESC`() {
        insertNotification("2026-05-01 08:00:00", "First", 300.00)
        insertNotification("2026-05-28 18:00:00", "Last", 10.00)

        mockMvc.perform(
            get("/payments/notifications")
                .param("month", "2026-05")
                .param("sort", "recent")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content[0].merchantName").value("Last"))
            .andExpect(jsonPath("$.content[1].merchantName").value("First"))
    }

    @Test
    fun `GET notifications with sort=amount orders by the installment due this month, not the purchase's total amount`() {
        // Notebook is 900.00 total in 3x (300.00/mo) — its May installment (300.00) is smaller
        // than the cash purchase below (500.00), so sort=amount must rank it AFTER. Sorting by
        // the raw pn.amount (900.00) would rank it first instead, which is exactly the bug this
        // proves fixed (PRD 3.1/3.2, Criterio de Sucesso #3 da Tarefa 4.0).
        jdbcTemplate.update(
            """INSERT INTO payment_notifications
               (card_last_digits, purchased_at, amount, merchant_name, number_of_installments, origin, origin_type, payment_method_id, group_id)
               VALUES ('1234', '2026-05-05 10:00:00', 900.00, 'Notebook Parcelado', 3, 'MANUAL', 'MANUAL', ?, 1)""",
            paymentMethodId,
        )
        val parceledId = jdbcTemplate.queryForObject(
            "SELECT id FROM payment_notifications WHERE merchant_name = 'Notebook Parcelado'", Long::class.java,
        )!!
        listOf(Triple(1, 3, "2026-05-05"), Triple(2, 3, "2026-06-05"), Triple(3, 3, "2026-07-05")).forEach { (number, total, dueDate) ->
            jdbcTemplate.update(
                """INSERT INTO installments (group_id, parent_id, installment_number, total_installments, amount, due_date)
                   VALUES (1, ?, ?, ?, 300.00, ?)""",
                parceledId, number, total, dueDate,
            )
        }

        insertNotification("2026-05-10 10:00:00", "Sofa a Vista", 500.00)

        mockMvc.perform(
            get("/payments/notifications")
                .param("month", "2026-05")
                .param("sort", "amount")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content[0].merchantName").value("Sofa a Vista"))
            .andExpect(jsonPath("$.content[1].merchantName").value("Notebook Parcelado"))
    }

    @Test
    fun `GET notifications with sort=amount paginates correctly`() {
        for (i in 1..5) {
            insertNotification("2026-05-${10 + i} 10:00:00", "Store $i", i * 10.0)
        }

        mockMvc.perform(
            get("/payments/notifications")
                .param("month", "2026-05")
                .param("sort", "amount")
                .param("page", "1")
                .param("size", "2")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content.length()").value(2))
            .andExpect(jsonPath("$.content[0].merchantName").value("Store 3"))
            .andExpect(jsonPath("$.content[1].merchantName").value("Store 2"))
            .andExpect(jsonPath("$.totalElements").value(5))
            .andExpect(jsonPath("$.totalPages").value(3))
    }

    @Test
    fun `GET notifications with invalid sort returns 400`() {
        mockMvc.perform(
            get("/payments/notifications")
                .param("month", "2026-05")
                .param("sort", "invalid")
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `GET notifications without new params preserves default behavior ordered by purchasedAt DESC`() {
        val subCardId = createSubCard(paymentMethodId)
        insertNotification("2026-05-01 08:00:00", "First", 300.00)
        insertNotification("2026-05-28 18:00:00", "Last", 10.00, paymentMethodId, subCardId)

        mockMvc.perform(get("/payments/notifications").param("month", "2026-05"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content.length()").value(2))
            .andExpect(jsonPath("$.content[0].merchantName").value("Last"))
            .andExpect(jsonPath("$.content[1].merchantName").value("First"))
            .andExpect(jsonPath("$.totalElements").value(2))
    }

    // --- Helpers ---

    private fun createHolder(): Long {
        jdbcTemplate.update("INSERT INTO holders (name, group_id) VALUES (?, 1)", "Test Holder")
        return jdbcTemplate.queryForObject("SELECT id FROM holders WHERE name = ?", Long::class.java, "Test Holder")!!
    }

    private fun createPaymentMethod(holderId: Long, name: String = "Test Card"): Long {
        jdbcTemplate.update(
            "INSERT INTO payment_methods (name, type, holder_id, group_id) VALUES (?, ?, ?, 1)",
            name,
            "CREDIT_CARD",
            holderId,
        )
        return jdbcTemplate.queryForObject("SELECT id FROM payment_methods WHERE name = ?", Long::class.java, name)!!
    }

    private fun createSubCard(paymentMethodId: Long, lastFourDigits: String = "5678"): Long {
        jdbcTemplate.update(
            "INSERT INTO sub_cards (payment_method_id, last_four_digits, type) VALUES (?, ?, ?)",
            paymentMethodId, lastFourDigits, "FISICO",
        )
        return jdbcTemplate.queryForObject("SELECT MAX(id) FROM sub_cards", Long::class.java)!!
    }

    // Every purchase now needs its own installment (statement) row after Tarefa 4.0's INNER
    // JOIN — this payment method has no closingDay, so its single installment falls back to the
    // calendar month/day of purchasedAt, same as BudgetPeriodCalculator.resolveInstallmentDueDate.
    private fun insertNotification(
        purchasedAt: String,
        merchantName: String,
        amount: Double,
        notificationPaymentMethodId: Long = paymentMethodId,
        subCardId: Long? = null,
    ): Long {
        jdbcTemplate.update(
            """INSERT INTO payment_notifications
               (card_last_digits, purchased_at, amount, merchant_name, number_of_installments, origin, origin_type, payment_method_id, sub_card_id, group_id)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 1)""",
            "1234", purchasedAt, amount, merchantName, 1, "MANUAL", "MANUAL", notificationPaymentMethodId, subCardId
        )
        val id = jdbcTemplate.queryForObject(
            "SELECT id FROM payment_notifications WHERE merchant_name = ?", Long::class.java, merchantName,
        )!!
        jdbcTemplate.update(
            """INSERT INTO installments (group_id, parent_id, installment_number, total_installments, amount, due_date)
               VALUES (1, ?, 1, 1, ?, ?)""",
            id, amount, purchasedAt.substring(0, 10),
        )
        return id
    }

    private fun insertDeletedNotification(purchasedAt: String, merchantName: String, amount: Double) {
        jdbcTemplate.update(
            """INSERT INTO payment_notifications
               (card_last_digits, purchased_at, amount, merchant_name, number_of_installments, origin, origin_type, payment_method_id, deleted_at, group_id)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, 1)""",
            "1234", purchasedAt, amount, merchantName, 1, "MANUAL", "MANUAL", paymentMethodId
        )
    }

    private fun insertNotificationWithoutPaymentMethod(purchasedAt: String, merchantName: String, amount: Double) {
        jdbcTemplate.update(
            """INSERT INTO payment_notifications
               (card_last_digits, purchased_at, amount, merchant_name, number_of_installments, origin, origin_type, group_id)
               VALUES (?, ?, ?, ?, ?, ?, ?, 1)""",
            "1234", purchasedAt, amount, merchantName, 1, "MANUAL", "MANUAL"
        )
    }
}
