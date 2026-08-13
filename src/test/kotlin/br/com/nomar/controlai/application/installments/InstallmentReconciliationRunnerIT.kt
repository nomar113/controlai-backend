package br.com.nomar.controlai.application.installments

import br.com.nomar.controlai.application.installments.application.InstallmentReconciliationRunner
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.DefaultApplicationArguments
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import java.math.BigDecimal
import java.time.LocalDate

@SpringBootTest
class InstallmentReconciliationRunnerIT {

    @Autowired private lateinit var runner: InstallmentReconciliationRunner
    @Autowired private lateinit var jdbcTemplate: JdbcTemplate

    private var missingParentId: Long = 0
    private var existingParentId: Long = 0
    private var cancelledParentId: Long = 0

    @BeforeEach
    fun setUp() {
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

        jdbcTemplate.update("INSERT INTO holders (name, group_id) VALUES ('Reconciliation Holder', 1)")
        val holderId = jdbcTemplate.queryForObject(
            "SELECT id FROM holders WHERE name = 'Reconciliation Holder'", Long::class.java
        )!!

        jdbcTemplate.update(
            "INSERT INTO payment_methods (name, type, holder_id, group_id, closing_day) VALUES ('Nubank', 'CREDIT_CARD', ?, 1, 10)",
            holderId,
        )
        val paymentMethodId = jdbcTemplate.queryForObject(
            "SELECT id FROM payment_methods WHERE name = 'Nubank'", Long::class.java
        )!!

        // Installment purchase with no rows in `installments` yet (fully pre-migration scenario).
        // Purchased before the closing day (10th) -> 1st installment falls in January's own cycle.
        jdbcTemplate.update(
            """INSERT INTO payment_notifications
               (purchased_at, amount, merchant_name, number_of_installments, origin, origin_type, group_id, payment_method_id)
               VALUES ('2026-01-05 10:00:00', 300.00, 'Loja Missing', 3, 'MANUAL', 'MANUAL', 1, ?)""",
            paymentMethodId,
        )
        missingParentId = jdbcTemplate.queryForObject(
            "SELECT id FROM payment_notifications WHERE merchant_name = 'Loja Missing'", Long::class.java
        )!!

        // Installment purchase with rows already computed by the old rule
        // (startDate.plusMonths(n-1), ignoring the card's closing day).
        jdbcTemplate.update(
            """INSERT INTO payment_notifications
               (purchased_at, amount, merchant_name, number_of_installments, origin, origin_type, group_id, payment_method_id)
               VALUES ('2026-01-15 10:00:00', 300.00, 'Loja Existing', 3, 'MANUAL', 'MANUAL', 1, ?)""",
            paymentMethodId,
        )
        existingParentId = jdbcTemplate.queryForObject(
            "SELECT id FROM payment_notifications WHERE merchant_name = 'Loja Existing'", Long::class.java
        )!!

        listOf(1 to "2026-01-15", 2 to "2026-02-15", 3 to "2026-03-15").forEach { (number, dueDate) ->
            jdbcTemplate.update(
                """INSERT INTO installments (group_id, parent_id, installment_number, total_installments, amount, due_date)
                   VALUES (1, ?, ?, 3, 100.00, ?)""",
                existingParentId, number, dueDate,
            )
        }

        // Cancelled installment purchase with no rows in `installments` — must not be revived.
        jdbcTemplate.update(
            """INSERT INTO payment_notifications
               (purchased_at, amount, merchant_name, number_of_installments, origin, origin_type, group_id, payment_method_id, cancelled_at)
               VALUES ('2026-01-05 10:00:00', 90.00, 'Loja Cancelled', 3, 'MANUAL', 'MANUAL', 1, ?, '2026-01-06 09:00:00')""",
            paymentMethodId,
        )
        cancelledParentId = jdbcTemplate.queryForObject(
            "SELECT id FROM payment_notifications WHERE merchant_name = 'Loja Cancelled'", Long::class.java
        )!!
    }

    @AfterEach
    fun tearDown() {
        jdbcTemplate.update("DELETE FROM budget_payment_periods")
        jdbcTemplate.update("DELETE FROM budget_incomes")
        jdbcTemplate.update("DELETE FROM budget_items")
        jdbcTemplate.update("DELETE FROM budgets")
    }

    @Test
    fun `should create missing installments and recalculate due dates using the closing day cycle`() {
        runner.run(DefaultApplicationArguments())

        val missing = jdbcTemplate.queryForList(
            "SELECT installment_number, amount, due_date FROM installments WHERE parent_id = ? ORDER BY installment_number",
            missingParentId,
        )
        assertEquals(3, missing.size)
        assertEquals(LocalDate.of(2026, 1, 5), (missing[0]["due_date"] as java.sql.Date).toLocalDate())
        assertEquals(LocalDate.of(2026, 2, 5), (missing[1]["due_date"] as java.sql.Date).toLocalDate())
        assertEquals(LocalDate.of(2026, 3, 5), (missing[2]["due_date"] as java.sql.Date).toLocalDate())
        assertEquals(BigDecimal("300.00"), missing.sumOf { it["amount"] as BigDecimal })

        val existing = jdbcTemplate.queryForList(
            "SELECT installment_number, amount, due_date FROM installments WHERE parent_id = ? ORDER BY installment_number",
            existingParentId,
        )
        assertEquals(3, existing.size)
        // Purchase on Jan 15 falls after the closing day (10th) -> cycle advances to February.
        assertEquals(LocalDate.of(2026, 2, 15), (existing[0]["due_date"] as java.sql.Date).toLocalDate())
        assertEquals(LocalDate.of(2026, 3, 15), (existing[1]["due_date"] as java.sql.Date).toLocalDate())
        assertEquals(LocalDate.of(2026, 4, 15), (existing[2]["due_date"] as java.sql.Date).toLocalDate())
        // Recalculation does not change the installment amount, only the due date.
        existing.forEach { assertEquals(BigDecimal("100.00"), it["amount"]) }
    }

    @Test
    fun `should not create installments for a cancelled purchase`() {
        runner.run(DefaultApplicationArguments())

        val count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM installments WHERE parent_id = ?", Int::class.java, cancelledParentId,
        )
        assertEquals(0, count)
    }

    @Test
    fun `should auto-create budgets for every month reached by reconciled installments`() {
        runner.run(DefaultApplicationArguments())

        listOf("2026-01", "2026-02", "2026-03", "2026-04").forEach { referenceMonth ->
            val count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM budgets WHERE group_id = 1 AND reference_month = ?",
                Int::class.java,
                referenceMonth,
            )
            assertEquals(1, count, "expected a budget for $referenceMonth")
        }
    }

    @Test
    fun `running the routine twice should be idempotent`() {
        runner.run(DefaultApplicationArguments())
        val firstRun = jdbcTemplate.queryForList(
            "SELECT parent_id, installment_number, amount, due_date FROM installments ORDER BY parent_id, installment_number"
        )
        val firstBudgetMonths = jdbcTemplate.queryForList(
            "SELECT reference_month FROM budgets WHERE group_id = 1 ORDER BY reference_month"
        )

        runner.run(DefaultApplicationArguments())
        val secondRun = jdbcTemplate.queryForList(
            "SELECT parent_id, installment_number, amount, due_date FROM installments ORDER BY parent_id, installment_number"
        )
        val secondBudgetMonths = jdbcTemplate.queryForList(
            "SELECT reference_month FROM budgets WHERE group_id = 1 ORDER BY reference_month"
        )

        assertEquals(firstRun, secondRun)
        assertEquals(firstBudgetMonths, secondBudgetMonths)
    }
}
