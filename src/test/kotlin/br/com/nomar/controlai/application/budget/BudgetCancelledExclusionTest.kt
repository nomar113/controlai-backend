package br.com.nomar.controlai.application.budget

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

@SpringBootTest
@AutoConfigureMockMvc
class BudgetCancelledExclusionTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach
    fun cleanUp() {
        jdbcTemplate.update("DELETE FROM installments")
        jdbcTemplate.update("DELETE FROM payment_notifications")
        jdbcTemplate.update("DELETE FROM budget_payment_periods")
        jdbcTemplate.update("DELETE FROM budget_incomes")
        jdbcTemplate.update("DELETE FROM budget_items")
        jdbcTemplate.update("DELETE FROM budgets")
        jdbcTemplate.update("DELETE FROM sub_cards")
        jdbcTemplate.update("DELETE FROM payment_methods")
        jdbcTemplate.update("DELETE FROM holders")
        jdbcTemplate.update("DELETE FROM categories")
    }

    @Test
    fun `GET budgets should exclude cancelled notifications from totals`() {
        // Setup: holder, payment method, budget, period, category
        val holderId = createHolder()
        val pmId = createPaymentMethod(holderId)
        val budgetId = createBudget("2026-03")
        createBudgetPaymentPeriod(budgetId, pmId, "2026-03-01", "2026-03-31")
        val categoryId = createCategory("Mercado")
        createBudgetItem(budgetId, categoryId, "EXPENSE", 1000.00)

        // Create 2 active notifications (100 + 200 = 300)
        insertNotification(pmId, categoryId, "2026-03-10 10:00:00", 100.00, null)
        insertNotification(pmId, categoryId, "2026-03-15 10:00:00", 200.00, null)

        // Create 1 cancelled notification (150 - should NOT count)
        insertNotification(pmId, categoryId, "2026-03-20 10:00:00", 150.00, "2026-03-21 10:00:00")

        mockMvc.perform(get("/budgets?month=2026-03"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalActual").value(300.00))
            .andExpect(jsonPath("$.items[0].actual").value(300.00))
    }

    // --- Helpers ---

    private fun createHolder(): Long {
        jdbcTemplate.update("INSERT INTO holders (name, group_id) VALUES ('Test Holder', 1)")
        return jdbcTemplate.queryForObject("SELECT MAX(id) FROM holders", Long::class.java)!!
    }

    private fun createPaymentMethod(holderId: Long): Long {
        jdbcTemplate.update(
            "INSERT INTO payment_methods (name, holder_id, type, group_id) VALUES ('Cartao Test', ?, 'CREDIT', 1)",
            holderId
        )
        return jdbcTemplate.queryForObject("SELECT MAX(id) FROM payment_methods", Long::class.java)!!
    }

    private fun createBudget(yearMonth: String): Long {
        jdbcTemplate.update("INSERT INTO budgets (reference_month, group_id) VALUES (?, 1)", yearMonth)
        return jdbcTemplate.queryForObject("SELECT MAX(id) FROM budgets", Long::class.java)!!
    }

    private fun createBudgetPaymentPeriod(budgetId: Long, pmId: Long, startDate: String, endDate: String) {
        jdbcTemplate.update(
            "INSERT INTO budget_payment_periods (budget_id, payment_method_id, start_date, end_date) VALUES (?, ?, ?, ?)",
            budgetId, pmId, startDate, endDate
        )
    }

    private fun createCategory(name: String): Long {
        jdbcTemplate.update("INSERT INTO categories (name, group_id) VALUES (?, 1)", name)
        return jdbcTemplate.queryForObject("SELECT MAX(id) FROM categories", Long::class.java)!!
    }

    private fun createBudgetItem(budgetId: Long, categoryId: Long, type: String, expected: Double) {
        jdbcTemplate.update(
            "INSERT INTO budget_items (budget_id, category_id, type, expected) VALUES (?, ?, ?, ?)",
            budgetId, categoryId, type, expected
        )
    }

    private fun insertNotification(pmId: Long, categoryId: Long, purchasedAt: String, amount: Double, cancelledAt: String?) {
        jdbcTemplate.update(
            """INSERT INTO payment_notifications (card_last_digits, purchased_at, amount, merchant_name, number_of_installments, origin, origin_type, payment_method_id, category_id, cancelled_at, group_id)
               VALUES ('1234', ?, ?, 'Store', 1, 'NUBANK', 'HTTP_REQUEST', ?, ?, ${if (cancelledAt != null) "?" else "NULL"}, 1)""",
            *listOfNotNull(purchasedAt, amount, pmId, categoryId, cancelledAt).toTypedArray()
        )
    }
}
