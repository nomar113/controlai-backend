package br.com.nomar.controlai.application.budget

import br.com.nomar.controlai.application.budget.application.EnsureFutureBudgetProvider
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import java.time.YearMonth
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@SpringBootTest
class EnsureFutureBudgetProviderTest {

    @Autowired private lateinit var ensureFutureBudgetProvider: EnsureFutureBudgetProvider
    @Autowired private lateinit var jdbcTemplate: JdbcTemplate

    private val groupId = 1L
    private var categoryId: Long = 0

    @BeforeEach
    fun cleanUp() {
        jdbcTemplate.update("DELETE FROM budget_payment_periods")
        jdbcTemplate.update("DELETE FROM budget_incomes")
        jdbcTemplate.update("DELETE FROM budget_items")
        jdbcTemplate.update("DELETE FROM budgets")
        jdbcTemplate.update("DELETE FROM categories")
        jdbcTemplate.update("INSERT INTO categories (name, group_id) VALUES ('Test Category', ?)", groupId)
        categoryId = jdbcTemplate.queryForObject("SELECT id FROM categories WHERE name = 'Test Category'", Long::class.java)!!
    }

    @AfterEach
    fun tearDown() {
        // EnsureFutureBudgetProvider.execute reads ambient payment_methods for the group and
        // links them via budget_payment_periods; without this, orphaned rows referencing those
        // shared-DB payment_methods break other test classes' cleanup (FK violation) by run order.
        jdbcTemplate.update("DELETE FROM budget_payment_periods")
        jdbcTemplate.update("DELETE FROM budget_incomes")
        jdbcTemplate.update("DELETE FROM budget_items")
        jdbcTemplate.update("DELETE FROM budgets")
        jdbcTemplate.update("DELETE FROM categories")
    }

    private fun insertBudget(yearMonth: String): Long {
        jdbcTemplate.update("INSERT INTO budgets (reference_month, group_id) VALUES (?, ?)", yearMonth, groupId)
        return jdbcTemplate.queryForObject("SELECT id FROM budgets WHERE reference_month = ? AND group_id = ?", Long::class.java, yearMonth, groupId)!!
    }

    private fun countBudgetsFor(yearMonth: String): Int =
        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM budgets WHERE reference_month = ? AND group_id = ?", Int::class.java, yearMonth, groupId)!!

    @Test
    fun `should return existing budget id without duplicating when budget already exists`() {
        val existingId = insertBudget("2026-05")

        val result = ensureFutureBudgetProvider.execute(groupId, YearMonth.of(2026, 5))

        assertTrue(result.isSuccess)
        assertEquals(existingId, result.getOrNull())
        assertEquals(1, countBudgetsFor("2026-05"))
    }

    @Test
    fun `should copy items and incomes from the most recent budget when target does not exist`() {
        val olderId = insertBudget("2026-03")
        val latestId = insertBudget("2026-04")

        jdbcTemplate.update(
            "INSERT INTO budget_items (budget_id, category_id, type, expected) VALUES (?, ?, ?, ?)",
            olderId, categoryId, "EXPENSE", 500.00
        )
        jdbcTemplate.update(
            "INSERT INTO budget_items (budget_id, category_id, type, expected) VALUES (?, ?, ?, ?)",
            latestId, categoryId, "EXPENSE", 2000.00
        )
        jdbcTemplate.update(
            "INSERT INTO budget_incomes (budget_id, label, amount) VALUES (?, ?, ?)",
            latestId, "Ramon Salario", 7000.00
        )

        val result = ensureFutureBudgetProvider.execute(groupId, YearMonth.of(2026, 5))

        assertTrue(result.isSuccess)
        val newBudgetId = result.getOrNull()!!
        assertEquals(1, countBudgetsFor("2026-05"))

        val items = jdbcTemplate.queryForList("SELECT * FROM budget_items WHERE budget_id = ?", newBudgetId)
        assertEquals(1, items.size)
        assertEquals(0, java.math.BigDecimal("2000.00").compareTo(items[0]["EXPECTED"] as java.math.BigDecimal))

        val incomes = jdbcTemplate.queryForList("SELECT * FROM budget_incomes WHERE budget_id = ?", newBudgetId)
        assertEquals(1, incomes.size)
        assertEquals("Ramon Salario", incomes[0]["LABEL"])
    }

    @Test
    fun `should create empty budget when no previous budget exists for the group`() {
        val result = ensureFutureBudgetProvider.execute(groupId, YearMonth.of(2026, 6))

        assertTrue(result.isSuccess)
        val newBudgetId = result.getOrNull()!!
        assertEquals(1, countBudgetsFor("2026-06"))

        val itemCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM budget_items WHERE budget_id = ?", Int::class.java, newBudgetId)!!
        val incomeCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM budget_incomes WHERE budget_id = ?", Int::class.java, newBudgetId)!!
        assertEquals(0, itemCount)
        assertEquals(0, incomeCount)
    }
}
