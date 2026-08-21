package br.com.nomar.controlai.application.installments

import br.com.nomar.controlai.application.installments.application.CreateInstallmentsProvider
import br.com.nomar.controlai.application.installments.entrypoint.database.repository.InstallmentRepository
import br.com.nomar.controlai.config.TestSecurityContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import java.math.BigDecimal
import java.time.LocalDate

@SpringBootTest
class CreateInstallmentsProviderTest {

    @Autowired private lateinit var createInstallmentsProvider: CreateInstallmentsProvider
    @Autowired private lateinit var installmentRepository: InstallmentRepository
    @Autowired private lateinit var jdbcTemplate: JdbcTemplate

    private var parentId: Long = 0

    @BeforeEach
    fun cleanUp() {
        TestSecurityContext.authenticateAsGroup()
        jdbcTemplate.update("DELETE FROM installments")
        jdbcTemplate.update("UPDATE payment_notifications SET category_id = NULL, payment_method_id = NULL, sub_card_id = NULL")
        jdbcTemplate.update("DELETE FROM payment_notifications")
        jdbcTemplate.update("DELETE FROM budget_payment_periods")
        jdbcTemplate.update("DELETE FROM budget_incomes")
        jdbcTemplate.update("DELETE FROM budget_items")
        jdbcTemplate.update("DELETE FROM budgets")
        jdbcTemplate.update("DELETE FROM sub_cards")
        jdbcTemplate.update("DELETE FROM payment_methods")
        jdbcTemplate.update("DELETE FROM holders")

        jdbcTemplate.update(
            """INSERT INTO payment_notifications
               (purchased_at, amount, merchant_name, number_of_installments, origin, origin_type, group_id)
               VALUES (CURRENT_TIMESTAMP, 589.90, 'Apple Store', 10, 'MANUAL', 'MANUAL', 1)"""
        )
        parentId = jdbcTemplate.queryForObject(
            "SELECT id FROM payment_notifications WHERE merchant_name = 'Apple Store'",
            Long::class.java
        )!!
    }

    @AfterEach
    fun clearAuth() = TestSecurityContext.clear()

    // No persisted budget exists for the months exercised below, so BudgetPeriodResolver falls
    // back to a fresh closingDay/type calculation for this payment method — same behavior the
    // old (closingDay, type) params exercised directly.
    private fun createPaymentMethod(type: String, closingDay: Int?): Long {
        val name = "Card ${System.nanoTime()}"
        jdbcTemplate.update("INSERT INTO holders (name, group_id) VALUES ('Test Holder', 1)")
        val holderId = jdbcTemplate.queryForObject(
            "SELECT id FROM holders WHERE name = 'Test Holder' ORDER BY id DESC LIMIT 1", Long::class.java
        )!!
        jdbcTemplate.update(
            "INSERT INTO payment_methods (name, type, holder_id, group_id, closing_day) VALUES (?, ?, ?, 1, ?)",
            name, type, holderId, closingDay,
        )
        return jdbcTemplate.queryForObject(
            "SELECT id FROM payment_methods WHERE name = ?", Long::class.java, name,
        )!!
    }

    @Test
    fun `should create N installments with correct sequential numbers`() {
        val result = createInstallmentsProvider.execute(
            parentId = parentId,
            groupId = 1L,
            totalInstallments = 3,
            totalAmount = BigDecimal("100.00"),
            startDate = LocalDate.of(2026, 5, 15),
        )

        assertEquals(3, result.size)
        assertEquals(1, result[0].installmentNumber)
        assertEquals(2, result[1].installmentNumber)
        assertEquals(3, result[2].installmentNumber)
        result.forEach {
            assertEquals(3, it.totalInstallments)
            assertEquals(parentId, it.parentId)
        }
        // 100.00 / 3 = 33.33 remainder 0.01 -> first gets 33.34
        assertEquals(BigDecimal("33.34"), result[0].amount)
        assertEquals(BigDecimal("33.33"), result[1].amount)
        assertEquals(BigDecimal("33.33"), result[2].amount)
    }

    @Test
    fun `should sum installment amounts back to total regardless of remainder`() {
        val totals = listOf("100.00", "589.90", "37.01", "999.99")
        val counts = listOf(3, 10, 7, 4)

        totals.zip(counts).forEach { (total, count) ->
            val previews = createInstallmentsProvider.calculate(
                totalInstallments = count,
                totalAmount = BigDecimal(total),
                startDate = LocalDate.of(2026, 5, 15),
                groupId = 1L,
            )
            val sum = previews.sumOf { it.amount }
            assertEquals(BigDecimal(total), sum, "sum of $count installments of $total should equal total")
        }
    }

    @Test
    fun `should fall back to calendar month when no payment method is provided`() {
        val result = createInstallmentsProvider.execute(
            parentId = parentId,
            groupId = 1L,
            totalInstallments = 3,
            totalAmount = BigDecimal("100.00"),
            startDate = LocalDate.of(2026, 1, 15),
        )

        assertEquals(LocalDate.of(2026, 1, 15), result[0].dueDate)
        assertEquals(LocalDate.of(2026, 2, 15), result[1].dueDate)
        assertEquals(LocalDate.of(2026, 3, 15), result[2].dueDate)
    }

    @Test
    fun `should fall back to calendar month when payment method is not a credit card`() {
        val paymentMethodId = createPaymentMethod("PIX", 10)
        val result = createInstallmentsProvider.execute(
            parentId = parentId,
            groupId = 1L,
            totalInstallments = 2,
            totalAmount = BigDecimal("50.00"),
            startDate = LocalDate.of(2026, 1, 15),
            paymentMethodId = paymentMethodId,
        )

        assertEquals(LocalDate.of(2026, 1, 15), result[0].dueDate)
        assertEquals(LocalDate.of(2026, 2, 15), result[1].dueDate)
    }

    @Test
    fun `should use card closing cycle when purchase is before closing day`() {
        val paymentMethodId = createPaymentMethod("CREDIT_CARD", 20)
        val result = createInstallmentsProvider.execute(
            parentId = parentId,
            groupId = 1L,
            totalInstallments = 2,
            totalAmount = BigDecimal("50.00"),
            startDate = LocalDate.of(2026, 1, 15),
            paymentMethodId = paymentMethodId,
        )

        assertEquals(LocalDate.of(2026, 1, 15), result[0].dueDate)
        assertEquals(LocalDate.of(2026, 2, 15), result[1].dueDate)
    }

    @Test
    fun `should advance cycle when purchase is after closing day`() {
        val paymentMethodId = createPaymentMethod("CREDIT_CARD", 10)
        val result = createInstallmentsProvider.execute(
            parentId = parentId,
            groupId = 1L,
            totalInstallments = 2,
            totalAmount = BigDecimal("50.00"),
            startDate = LocalDate.of(2026, 1, 15),
            paymentMethodId = paymentMethodId,
        )

        // Purchase (Jan 15) falls after the Jan 10 closing day, so the 1st installment
        // is billed in the February cycle instead of the purchase month.
        assertEquals(LocalDate.of(2026, 2, 15), result[0].dueDate)
        assertEquals(LocalDate.of(2026, 3, 15), result[1].dueDate)
    }

    @Test
    fun `should honor a manually edited period over the card's raw closing day`() {
        // Card closes day 1 (so a Jan 15 purchase would normally land in February), but the
        // January budget's persisted period for this card was edited to run through Jan 31 —
        // installment placement must follow that persisted period, not the raw closingDay.
        val paymentMethodId = createPaymentMethod("CREDIT_CARD", 1)
        jdbcTemplate.update("INSERT INTO budgets (reference_month, group_id) VALUES ('2026-01', 1)")
        val budgetId = jdbcTemplate.queryForObject(
            "SELECT id FROM budgets WHERE reference_month = '2026-01'", Long::class.java,
        )!!
        jdbcTemplate.update(
            "INSERT INTO budget_payment_periods (budget_id, payment_method_id, start_date, end_date) VALUES (?, ?, '2026-01-01', '2026-01-31')",
            budgetId, paymentMethodId,
        )

        val result = createInstallmentsProvider.execute(
            parentId = parentId,
            groupId = 1L,
            totalInstallments = 2,
            totalAmount = BigDecimal("50.00"),
            startDate = LocalDate.of(2026, 1, 15),
            paymentMethodId = paymentMethodId,
        )

        assertEquals(LocalDate.of(2026, 1, 15), result[0].dueDate)
        assertEquals(LocalDate.of(2026, 2, 15), result[1].dueDate)
    }

    @Test
    fun `should clamp day 31 to shorter target months when advancing cycles`() {
        val result = createInstallmentsProvider.execute(
            parentId = parentId,
            groupId = 1L,
            totalInstallments = 3,
            totalAmount = BigDecimal("100.00"),
            startDate = LocalDate.of(2026, 1, 31),
        )

        assertEquals(LocalDate.of(2026, 1, 31), result[0].dueDate)
        assertEquals(LocalDate.of(2026, 2, 28), result[1].dueDate) // Feb 2026 has 28 days
        assertEquals(LocalDate.of(2026, 3, 31), result[2].dueDate)
    }

    @Test
    fun `should create single installment`() {
        val result = createInstallmentsProvider.execute(
            parentId = parentId,
            groupId = 1L,
            totalInstallments = 1,
            totalAmount = BigDecimal("900.00"),
            startDate = LocalDate.of(2026, 5, 10),
        )

        assertEquals(1, result.size)
        assertEquals(1, result[0].installmentNumber)
        assertEquals(1, result[0].totalInstallments)
        assertEquals(LocalDate.of(2026, 5, 10), result[0].dueDate)
    }

    @Test
    fun `should split total evenly when divisible`() {
        val result = createInstallmentsProvider.execute(
            parentId = parentId,
            groupId = 1L,
            totalInstallments = 4,
            totalAmount = BigDecimal("100.00"),
            startDate = LocalDate.of(2026, 5, 15),
        )

        assertEquals(4, result.size)
        result.forEach { assertEquals(BigDecimal("25.00"), it.amount) }
    }

    @Test
    fun `should put remainder cents in first installment`() {
        // 589.90 / 10 = 58.99 each, no remainder
        val result = createInstallmentsProvider.execute(
            parentId = parentId,
            groupId = 1L,
            totalInstallments = 10,
            totalAmount = BigDecimal("589.90"),
            startDate = LocalDate.of(2026, 5, 15),
        )

        assertEquals(10, result.size)
        result.forEach { assertEquals(BigDecimal("58.99"), it.amount) }
    }

    @Test
    fun `should handle remainder of multiple cents`() {
        // 100.00 / 7 = 14.28 base, remainder = 100.00 - 14.28*7 = 100.00 - 99.96 = 0.04
        val result = createInstallmentsProvider.execute(
            parentId = parentId,
            groupId = 1L,
            totalInstallments = 7,
            totalAmount = BigDecimal("100.00"),
            startDate = LocalDate.of(2026, 5, 15),
        )

        assertEquals(7, result.size)
        assertEquals(BigDecimal("14.32"), result[0].amount)
        for (i in 1..6) {
            assertEquals(BigDecimal("14.28"), result[i].amount)
        }
    }

    @Test
    fun `executeWithAmounts should also advance the cycle using the card's closing day`() {
        val paymentMethodId = createPaymentMethod("CREDIT_CARD", 10)
        val result = createInstallmentsProvider.executeWithAmounts(
            parentId = parentId,
            groupId = 1L,
            totalInstallments = 2,
            amounts = mapOf(1 to BigDecimal("30.00"), 2 to BigDecimal("20.00")),
            startDate = LocalDate.of(2026, 1, 15),
            paymentMethodId = paymentMethodId,
        )

        assertEquals(LocalDate.of(2026, 2, 15), result[0].dueDate)
        assertEquals(LocalDate.of(2026, 3, 15), result[1].dueDate)
        assertEquals(BigDecimal("50.00"), result.sumOf { it.amount })
    }
}
