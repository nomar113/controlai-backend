package br.com.nomar.controlai.application.payments_notification.application

import br.com.nomar.controlai.application.payments_notification.entrypoint.database.model.PaymentNotification
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Exercises SavePaymentNotificationProvider by calling it directly (no MockMvc, no
// TestSecurityContext) to mirror how the SQS queue listener invokes it: on a background
// thread with no HTTP request bound, so no @RequestScope bean (RequestContext) may be
// touched anywhere in this path.
@SpringBootTest
class SavePaymentNotificationProviderTest {

    @Autowired private lateinit var savePaymentNotificationProvider: SavePaymentNotificationProvider
    @Autowired private lateinit var jdbcTemplate: JdbcTemplate

    private val groupId = 1L
    private var holderId: Long = 0

    @BeforeEach
    fun cleanUp() {
        jdbcTemplate.update("DELETE FROM installments")
        jdbcTemplate.update("UPDATE payment_notifications SET category_id = NULL, payment_method_id = NULL, sub_card_id = NULL")
        jdbcTemplate.update("DELETE FROM payment_notifications")
        jdbcTemplate.update("DELETE FROM budget_payment_periods")
        jdbcTemplate.update("DELETE FROM budget_incomes")
        jdbcTemplate.update("DELETE FROM budget_items")
        jdbcTemplate.update("DELETE FROM budgets")
        jdbcTemplate.update("DELETE FROM payment_methods")
        jdbcTemplate.update("DELETE FROM holders")

        jdbcTemplate.update("INSERT INTO holders (name, group_id) VALUES ('Titular Teste', ?)", groupId)
        holderId = jdbcTemplate.queryForObject("SELECT id FROM holders WHERE name = 'Titular Teste'", Long::class.java)!!
    }

    @AfterEach
    fun tearDown() {
        // createInstallments() ensures a budget exists for each installment's cycle via
        // EnsureFutureBudgetProvider, which reads ambient payment_methods for the group; leaving
        // those rows behind breaks other suites' cleanup by FK, same lesson as
        // EnsureFutureBudgetProviderTest.
        jdbcTemplate.update("DELETE FROM installments")
        jdbcTemplate.update("DELETE FROM payment_notifications")
        jdbcTemplate.update("DELETE FROM budget_payment_periods")
        jdbcTemplate.update("DELETE FROM budget_incomes")
        jdbcTemplate.update("DELETE FROM budget_items")
        jdbcTemplate.update("DELETE FROM budgets")
        jdbcTemplate.update("DELETE FROM payment_methods")
        jdbcTemplate.update("DELETE FROM holders")
    }

    private fun insertCreditCard(closingDay: Int = 10): Long {
        jdbcTemplate.update(
            "INSERT INTO payment_methods (name, type, holder_id, closing_day, group_id) VALUES ('Nubank', 'CREDIT_CARD', ?, ?, ?)",
            holderId, closingDay, groupId,
        )
        return jdbcTemplate.queryForObject("SELECT MAX(id) FROM payment_methods", Long::class.java)!!
    }

    private fun countInstallmentsByParent(parentId: Long): Int =
        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM installments WHERE parent_id = ?", Int::class.java, parentId)!!

    private fun countBudgetsFor(yearMonth: String): Int =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM budgets WHERE reference_month = ? AND group_id = ?",
            Int::class.java, yearMonth, groupId,
        )!!

    @Test
    fun `should create N installments automatically for a simulated SMS notification with no HTTP request bound`() {
        val paymentMethodId = insertCreditCard(closingDay = 10)
        // Purchase on the 15th, after the 10th closing day, so the 1st installment bills in
        // the next cycle (Feb) instead of the purchase month (Jan).
        val notification = PaymentNotification(
            groupId = groupId,
            purchasedAt = LocalDateTime.of(2026, 1, 15, 10, 0),
            amount = BigDecimal("300.00"),
            merchantName = "Apple Store",
            numberOfInstallments = 3,
            origin = "NUBANK",
            originType = "SMS",
            paymentMethodId = paymentMethodId,
        )

        val result = savePaymentNotificationProvider.execute(notification)

        assertTrue(result.isSuccess)
        val saved = result.getOrNull()!!
        assertEquals(3, countInstallmentsByParent(saved.id))

        val dueDates = jdbcTemplate.queryForList(
            "SELECT due_date FROM installments WHERE parent_id = ? ORDER BY installment_number",
            java.sql.Date::class.java, saved.id,
        ).map { it.toLocalDate() }
        assertEquals(
            listOf(LocalDate.of(2026, 2, 15), LocalDate.of(2026, 3, 15), LocalDate.of(2026, 4, 15)),
            dueDates,
        )
    }

    @Test
    fun `should create future budgets for every cycle an installment falls into`() {
        val paymentMethodId = insertCreditCard(closingDay = 10)
        val notification = PaymentNotification(
            groupId = groupId,
            purchasedAt = LocalDateTime.of(2026, 1, 15, 10, 0),
            amount = BigDecimal("300.00"),
            merchantName = "Apple Store",
            numberOfInstallments = 3,
            origin = "NUBANK",
            originType = "SMS",
            paymentMethodId = paymentMethodId,
        )

        savePaymentNotificationProvider.execute(notification).getOrThrow()

        assertEquals(1, countBudgetsFor("2026-02"))
        assertEquals(1, countBudgetsFor("2026-03"))
        assertEquals(1, countBudgetsFor("2026-04"))
    }

    @Test
    fun `should not create installments for a cash purchase`() {
        val notification = PaymentNotification(
            groupId = groupId,
            purchasedAt = LocalDateTime.of(2026, 1, 15, 10, 0),
            amount = BigDecimal("89.90"),
            merchantName = "Padaria",
            numberOfInstallments = 1,
            origin = "NUBANK",
            originType = "SMS",
        )

        val result = savePaymentNotificationProvider.execute(notification)

        assertTrue(result.isSuccess)
        assertEquals(0, countInstallmentsByParent(result.getOrNull()!!.id))
    }

    @Test
    fun `should fall back to calendar month cycle when payment method has no closing day`() {
        val notification = PaymentNotification(
            groupId = groupId,
            purchasedAt = LocalDateTime.of(2026, 1, 15, 10, 0),
            amount = BigDecimal("200.00"),
            merchantName = "Loja Sem Cartao",
            numberOfInstallments = 2,
            origin = "MANUAL",
            originType = "MANUAL",
        )

        val saved = savePaymentNotificationProvider.execute(notification).getOrThrow()

        val dueDates = jdbcTemplate.queryForList(
            "SELECT due_date FROM installments WHERE parent_id = ? ORDER BY installment_number",
            java.sql.Date::class.java, saved.id,
        ).map { it.toLocalDate() }
        assertEquals(listOf(LocalDate.of(2026, 1, 15), LocalDate.of(2026, 2, 15)), dueDates)
    }
}
