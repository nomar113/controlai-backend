package br.com.nomar.controlai.application.budget

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

@SpringBootTest
@AutoConfigureMockMvc
class BudgetControllerIntegrationTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper
    @Autowired private lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach
    fun cleanUp() {
        jdbcTemplate.update("DELETE FROM budget_incomes")
        jdbcTemplate.update("DELETE FROM budget_items")
        jdbcTemplate.update("DELETE FROM budgets")
        jdbcTemplate.update("UPDATE payment_notifications SET category_id = NULL")
        jdbcTemplate.update("UPDATE purchase_invoices SET category_id = NULL")
        jdbcTemplate.update("DELETE FROM categories")
    }

    @Test
    fun `GET budgets should return categoryIcon in items`() {
        val categoryId = createCategory("Mercado", "🛒")
        val budgetId = createBudgetViaJdbc("2026-11")
        createBudgetItemViaJdbc(budgetId, categoryId, "EXPENSE", 2000.00)

        mockMvc.perform(get("/budgets?month=2026-11"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items[0].categoryName").value("Mercado"))
            .andExpect(jsonPath("$.items[0].categoryIcon").value("🛒"))
    }

    @Test
    fun `GET budgets should return null categoryIcon when category has no icon`() {
        val categoryId = createCategory("Moradia", null)
        val budgetId = createBudgetViaJdbc("2026-12")
        createBudgetItemViaJdbc(budgetId, categoryId, "EXPENSE", 1500.00)

        mockMvc.perform(get("/budgets?month=2026-12"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items[0].categoryName").value("Moradia"))
            .andExpect(jsonPath("$.items[0].categoryIcon").doesNotExist())
    }

    @Test
    fun `GET budgets auto-creates budget when it does not exist`() {
        mockMvc.perform(get("/budgets?month=2099-12"))
            .andExpect(status().isOk)
    }

    // --- Helpers ---

    private fun createCategory(name: String, icon: String?): Long {
        jdbcTemplate.update(
            "INSERT INTO categories (name, icon) VALUES (?, ?)",
            name, icon
        )
        return jdbcTemplate.queryForList(
            "SELECT id FROM categories WHERE name = ?",
            name
        ).first()["ID"] as Long
    }

    private fun createBudgetViaJdbc(yearMonth: String): Long {
        jdbcTemplate.update("INSERT INTO budgets (reference_month) VALUES (?)", yearMonth)
        return jdbcTemplate.queryForList(
            "SELECT id FROM budgets WHERE reference_month = ?", yearMonth
        ).first()["ID"] as Long
    }

    private fun createBudgetItemViaJdbc(budgetId: Long, categoryId: Long, type: String, expected: Double) {
        jdbcTemplate.update(
            "INSERT INTO budget_items (budget_id, category_id, type, expected) VALUES (?, ?, ?, ?)",
            budgetId, categoryId, type, expected
        )
    }
}
