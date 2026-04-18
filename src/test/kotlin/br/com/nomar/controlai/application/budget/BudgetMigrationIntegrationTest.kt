package br.com.nomar.controlai.application.budget

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@SpringBootTest
class BudgetMigrationIntegrationTest {

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach
    fun cleanUp() {
        jdbcTemplate.update("DELETE FROM budget_incomes")
        jdbcTemplate.update("DELETE FROM budget_items")
        jdbcTemplate.update("DELETE FROM budgets")
    }

    @Test
    fun `should create budgets table`() {
        val count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM information_schema.tables WHERE UPPER(table_name) = 'BUDGETS'",
            Int::class.java
        )
        assertEquals(1, count)
    }

    @Test
    fun `should create budget_items table`() {
        val count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM information_schema.tables WHERE UPPER(table_name) = 'BUDGET_ITEMS'",
            Int::class.java
        )
        assertEquals(1, count)
    }

    @Test
    fun `should create budget_incomes table`() {
        val count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM information_schema.tables WHERE UPPER(table_name) = 'BUDGET_INCOMES'",
            Int::class.java
        )
        assertEquals(1, count)
    }

    @Test
    fun `should enforce unique constraint on reference_month`() {
        jdbcTemplate.update("INSERT INTO budgets (reference_month) VALUES (?)", "2026-04")

        val exception = runCatching {
            jdbcTemplate.update("INSERT INTO budgets (reference_month) VALUES (?)", "2026-04")
        }
        assertTrue(exception.isFailure)
    }

    @Test
    fun `should enforce unique constraint on budget_id and category_id`() {
        jdbcTemplate.update("INSERT INTO budgets (reference_month) VALUES (?)", "2026-05")
        val budgetId = jdbcTemplate.queryForObject(
            "SELECT id FROM budgets WHERE reference_month = '2026-05'", Long::class.java
        )

        val categoryId = jdbcTemplate.queryForObject(
            "SELECT id FROM categories LIMIT 1", Long::class.java
        )

        jdbcTemplate.update(
            "INSERT INTO budget_items (budget_id, category_id, type, expected) VALUES (?, ?, ?, ?)",
            budgetId, categoryId, "EXPENSE", 1000.00
        )

        val exception = runCatching {
            jdbcTemplate.update(
                "INSERT INTO budget_items (budget_id, category_id, type, expected) VALUES (?, ?, ?, ?)",
                budgetId, categoryId, "EXPENSE", 2000.00
            )
        }
        assertTrue(exception.isFailure)
    }

    @Test
    fun `should insert budget with items and incomes`() {
        jdbcTemplate.update("INSERT INTO budgets (reference_month) VALUES (?)", "2026-06")
        val budgetId = jdbcTemplate.queryForObject(
            "SELECT id FROM budgets WHERE reference_month = '2026-06'", Long::class.java
        )

        val categoryId = jdbcTemplate.queryForObject(
            "SELECT id FROM categories LIMIT 1", Long::class.java
        )

        jdbcTemplate.update(
            "INSERT INTO budget_items (budget_id, category_id, type, expected) VALUES (?, ?, ?, ?)",
            budgetId, categoryId, "EXPENSE", 2000.00
        )

        jdbcTemplate.update(
            "INSERT INTO budget_incomes (budget_id, label, amount) VALUES (?, ?, ?)",
            budgetId, "Ramon Salario", 7000.00
        )

        val items = jdbcTemplate.queryForList(
            "SELECT * FROM budget_items WHERE budget_id = ?", budgetId
        )
        assertEquals(1, items.size)
        assertEquals("EXPENSE", items[0]["TYPE"])

        val incomes = jdbcTemplate.queryForList(
            "SELECT * FROM budget_incomes WHERE budget_id = ?", budgetId
        )
        assertEquals(1, incomes.size)
        assertEquals("Ramon Salario", incomes[0]["LABEL"])
    }

    @Test
    fun `should cascade delete items and incomes when budget is deleted`() {
        jdbcTemplate.update("INSERT INTO budgets (reference_month) VALUES (?)", "2026-07")
        val budgetId = jdbcTemplate.queryForObject(
            "SELECT id FROM budgets WHERE reference_month = '2026-07'", Long::class.java
        )

        val categoryId = jdbcTemplate.queryForObject(
            "SELECT id FROM categories LIMIT 1", Long::class.java
        )

        jdbcTemplate.update(
            "INSERT INTO budget_items (budget_id, category_id, type, expected) VALUES (?, ?, ?, ?)",
            budgetId, categoryId, "INVESTMENT", 1500.00
        )
        jdbcTemplate.update(
            "INSERT INTO budget_incomes (budget_id, label, amount) VALUES (?, ?, ?)",
            budgetId, "Aline Salario", 5000.00
        )

        // Delete budget — should cascade
        jdbcTemplate.update("DELETE FROM budgets WHERE id = ?", budgetId)

        val itemCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM budget_items WHERE budget_id = ?", Int::class.java, budgetId
        )
        val incomeCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM budget_incomes WHERE budget_id = ?", Int::class.java, budgetId
        )

        assertEquals(0, itemCount)
        assertEquals(0, incomeCount)
    }

    @Test
    fun `should have created_at and updated_at columns on all tables`() {
        val tables = listOf("BUDGETS", "BUDGET_ITEMS", "BUDGET_INCOMES")

        for (table in tables) {
            val columns = jdbcTemplate.queryForList(
                "SELECT UPPER(column_name) AS col FROM information_schema.columns WHERE UPPER(table_name) = ? AND UPPER(column_name) IN ('CREATED_AT', 'UPDATED_AT')",
                table
            ).map { it["COL"] as String }

            assertTrue(columns.contains("CREATED_AT"), "$table should have created_at")
            assertTrue(columns.contains("UPDATED_AT"), "$table should have updated_at")
        }
    }
}
