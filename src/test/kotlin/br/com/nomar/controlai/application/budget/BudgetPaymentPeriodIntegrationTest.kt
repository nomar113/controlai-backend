package br.com.nomar.controlai.application.budget

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@SpringBootTest
class BudgetPaymentPeriodIntegrationTest {

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach
    fun cleanUp() {
        jdbcTemplate.update("DELETE FROM budget_payment_periods")
        jdbcTemplate.update("DELETE FROM budget_incomes")
        jdbcTemplate.update("DELETE FROM budget_items")
        jdbcTemplate.update("DELETE FROM budgets")
    }

    private fun createBudget(yearMonth: String): Long {
        jdbcTemplate.update("INSERT INTO budgets (reference_month, group_id) VALUES (?, 1)", yearMonth)
        return jdbcTemplate.queryForObject(
            "SELECT id FROM budgets WHERE reference_month = ?", Long::class.java, yearMonth
        )!!
    }

    private fun getPaymentMethodId(): Long {
        return jdbcTemplate.queryForObject(
            "SELECT id FROM payment_methods LIMIT 1", Long::class.java
        )!!
    }

    @Test
    fun `should create budget_payment_periods table`() {
        val count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM information_schema.tables WHERE UPPER(table_name) = 'BUDGET_PAYMENT_PERIODS'",
            Int::class.java
        )
        assertEquals(1, count)
    }

    @Test
    fun `should save and retrieve budget payment period`() {
        val budgetId = createBudget("2026-05")
        val paymentMethodId = getPaymentMethodId()

        jdbcTemplate.update(
            "INSERT INTO budget_payment_periods (budget_id, payment_method_id, start_date, end_date) VALUES (?, ?, ?, ?)",
            budgetId, paymentMethodId, "2026-04-11", "2026-05-10"
        )

        val periods = jdbcTemplate.queryForList(
            "SELECT * FROM budget_payment_periods WHERE budget_id = ?", budgetId
        )
        assertEquals(1, periods.size)
        assertEquals(paymentMethodId, periods[0]["PAYMENT_METHOD_ID"] as Long)
        assertEquals(java.sql.Date.valueOf("2026-04-11"), periods[0]["START_DATE"])
        assertEquals(java.sql.Date.valueOf("2026-05-10"), periods[0]["END_DATE"])
    }

    @Test
    fun `should enforce unique constraint on budget_id and payment_method_id`() {
        val budgetId = createBudget("2026-06")
        val paymentMethodId = getPaymentMethodId()

        jdbcTemplate.update(
            "INSERT INTO budget_payment_periods (budget_id, payment_method_id, start_date, end_date) VALUES (?, ?, ?, ?)",
            budgetId, paymentMethodId, "2026-05-11", "2026-06-10"
        )

        val exception = runCatching {
            jdbcTemplate.update(
                "INSERT INTO budget_payment_periods (budget_id, payment_method_id, start_date, end_date) VALUES (?, ?, ?, ?)",
                budgetId, paymentMethodId, "2026-05-15", "2026-06-15"
            )
        }
        assertTrue(exception.isFailure)
    }

    @Test
    fun `should cascade delete payment periods when budget is deleted`() {
        val budgetId = createBudget("2026-07")
        val paymentMethodId = getPaymentMethodId()

        jdbcTemplate.update(
            "INSERT INTO budget_payment_periods (budget_id, payment_method_id, start_date, end_date) VALUES (?, ?, ?, ?)",
            budgetId, paymentMethodId, "2026-06-11", "2026-07-10"
        )

        val countBefore = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM budget_payment_periods WHERE budget_id = ?", Int::class.java, budgetId
        )
        assertEquals(1, countBefore)

        jdbcTemplate.update("DELETE FROM budgets WHERE id = ?", budgetId)

        val countAfter = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM budget_payment_periods WHERE budget_id = ?", Int::class.java, budgetId
        )
        assertEquals(0, countAfter)
    }

    @Test
    fun `should have created_at and updated_at columns`() {
        val columns = jdbcTemplate.queryForList(
            "SELECT UPPER(column_name) AS col FROM information_schema.columns WHERE UPPER(table_name) = 'BUDGET_PAYMENT_PERIODS' AND UPPER(column_name) IN ('CREATED_AT', 'UPDATED_AT')"
        ).map { it["COL"] as String }

        assertTrue(columns.contains("CREATED_AT"), "should have created_at")
        assertTrue(columns.contains("UPDATED_AT"), "should have updated_at")
    }
}
