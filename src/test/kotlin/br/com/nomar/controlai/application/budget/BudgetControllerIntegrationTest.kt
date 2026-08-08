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
import kotlin.test.assertEquals

@SpringBootTest
@AutoConfigureMockMvc
class BudgetControllerIntegrationTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper
    @Autowired private lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach
    fun cleanUp() {
        jdbcTemplate.update("DELETE FROM budget_payment_periods")
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
    fun `GET budgets returns 404 and does not create a budget when it does not exist`() {
        mockMvc.perform(get("/budgets?month=2099-12"))
            .andExpect(status().isNotFound)

        val count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM budgets WHERE reference_month = ?", Long::class.java, "2099-12"
        )
        assertEquals(0L, count)
    }

    @Test
    fun `PUT budgets periods returns updated empty and failed empty when replicateToFuture omitted`() {
        val currentBudgetId = createBudgetViaJdbc("2026-05")
        val paymentMethodId = firstPaymentMethodId()

        val body = """
            {
              "periods": [
                { "paymentMethodId": $paymentMethodId, "startDate": "2026-04-10", "endDate": "2026-05-09" }
              ]
            }
        """.trimIndent()

        mockMvc.perform(
            put("/budgets/$currentBudgetId/periods")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.updated").isArray)
            .andExpect(jsonPath("$.updated.length()").value(0))
            .andExpect(jsonPath("$.failed.length()").value(0))

        val periods = jdbcTemplate.queryForList(
            "SELECT * FROM budget_payment_periods WHERE budget_id = ?", currentBudgetId
        )
        assertEquals(1, periods.size)
    }

    @Test
    fun `PUT budgets periods with replicateToFuture true propagates dates to future budgets`() {
        val currentBudgetId = createBudgetViaJdbc("2026-05")
        val futureBudgetId1 = createBudgetViaJdbc("2026-06")
        val futureBudgetId2 = createBudgetViaJdbc("2026-07")
        val pastBudgetId = createBudgetViaJdbc("2026-04")
        val paymentMethodId = firstPaymentMethodId()

        insertPeriod(futureBudgetId1, paymentMethodId, "2026-05-09", "2026-06-08")
        insertPeriod(futureBudgetId2, paymentMethodId, "2026-06-09", "2026-07-08")
        insertPeriod(pastBudgetId, paymentMethodId, "2026-03-09", "2026-04-08")

        val body = """
            {
              "periods": [
                { "paymentMethodId": $paymentMethodId, "startDate": "2026-04-10", "endDate": "2026-05-09" }
              ],
              "replicateToFuture": true
            }
        """.trimIndent()

        mockMvc.perform(
            put("/budgets/$currentBudgetId/periods")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.updated.length()").value(2))
            .andExpect(jsonPath("$.failed.length()").value(0))

        val future1Period = jdbcTemplate.queryForMap(
            "SELECT start_date, end_date FROM budget_payment_periods WHERE budget_id = ?", futureBudgetId1
        )
        assertEquals(java.sql.Date.valueOf("2026-05-10"), future1Period["START_DATE"])
        assertEquals(java.sql.Date.valueOf("2026-06-09"), future1Period["END_DATE"])

        val future2Period = jdbcTemplate.queryForMap(
            "SELECT start_date, end_date FROM budget_payment_periods WHERE budget_id = ?", futureBudgetId2
        )
        assertEquals(java.sql.Date.valueOf("2026-06-10"), future2Period["START_DATE"])
        assertEquals(java.sql.Date.valueOf("2026-07-09"), future2Period["END_DATE"])

        // Past budget should NOT be affected
        val pastPeriod = jdbcTemplate.queryForMap(
            "SELECT start_date, end_date FROM budget_payment_periods WHERE budget_id = ?", pastBudgetId
        )
        assertEquals(java.sql.Date.valueOf("2026-03-09"), pastPeriod["START_DATE"])
        assertEquals(java.sql.Date.valueOf("2026-04-08"), pastPeriod["END_DATE"])
    }

    @Test
    fun `PUT budgets periods with replicateToFuture true does not affect another card's period in the future budget`() {
        val currentBudgetId = createBudgetViaJdbc("2026-05")
        val futureBudgetId = createBudgetViaJdbc("2026-06")
        val cardAId = firstPaymentMethodId()
        val cardBId = createPaymentMethodViaJdbc("Other Card", 20)

        insertPeriod(futureBudgetId, cardAId, "2026-05-09", "2026-06-08")
        insertPeriod(futureBudgetId, cardBId, "2026-05-19", "2026-06-18")

        val body = """
            {
              "periods": [
                { "paymentMethodId": $cardAId, "startDate": "2026-04-10", "endDate": "2026-05-09" }
              ],
              "replicateToFuture": true
            }
        """.trimIndent()

        mockMvc.perform(
            put("/budgets/$currentBudgetId/periods")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
        ).andExpect(status().isOk)

        val cardAFuturePeriod = jdbcTemplate.queryForMap(
            "SELECT start_date, end_date FROM budget_payment_periods WHERE budget_id = ? AND payment_method_id = ?",
            futureBudgetId, cardAId
        )
        assertEquals(java.sql.Date.valueOf("2026-05-10"), cardAFuturePeriod["START_DATE"])
        assertEquals(java.sql.Date.valueOf("2026-06-09"), cardAFuturePeriod["END_DATE"])

        // Card B was not part of the replicated request and must remain untouched
        val cardBFuturePeriod = jdbcTemplate.queryForMap(
            "SELECT start_date, end_date FROM budget_payment_periods WHERE budget_id = ? AND payment_method_id = ?",
            futureBudgetId, cardBId
        )
        assertEquals(java.sql.Date.valueOf("2026-05-19"), cardBFuturePeriod["START_DATE"])
        assertEquals(java.sql.Date.valueOf("2026-06-18"), cardBFuturePeriod["END_DATE"])
    }

    @Test
    fun `PUT budgets periods updating one card does not affect another card's period`() {
        val budgetId = createBudgetViaJdbc("2026-05")
        val cardAId = firstPaymentMethodId()
        val cardBId = createPaymentMethodViaJdbc("Other Card", 20)

        insertPeriod(budgetId, cardAId, "2026-04-10", "2026-05-09")
        insertPeriod(budgetId, cardBId, "2026-04-20", "2026-05-19")

        val body = """
            {
              "periods": [
                { "paymentMethodId": $cardAId, "startDate": "2026-04-15", "endDate": "2026-05-14" }
              ]
            }
        """.trimIndent()

        mockMvc.perform(
            put("/budgets/$budgetId/periods")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
        ).andExpect(status().isOk)

        val cardAPeriod = jdbcTemplate.queryForMap(
            "SELECT start_date, end_date FROM budget_payment_periods WHERE budget_id = ? AND payment_method_id = ?",
            budgetId, cardAId
        )
        assertEquals(java.sql.Date.valueOf("2026-04-15"), cardAPeriod["START_DATE"])
        assertEquals(java.sql.Date.valueOf("2026-05-14"), cardAPeriod["END_DATE"])

        // Card B was not part of the request and must remain untouched
        val cardBPeriod = jdbcTemplate.queryForMap(
            "SELECT start_date, end_date FROM budget_payment_periods WHERE budget_id = ? AND payment_method_id = ?",
            budgetId, cardBId
        )
        assertEquals(java.sql.Date.valueOf("2026-04-20"), cardBPeriod["START_DATE"])
        assertEquals(java.sql.Date.valueOf("2026-05-19"), cardBPeriod["END_DATE"])

        val allPeriods = jdbcTemplate.queryForList(
            "SELECT * FROM budget_payment_periods WHERE budget_id = ?", budgetId
        )
        assertEquals(2, allPeriods.size)
    }

    @Test
    fun `PUT budgets periods rejects invalid date range with 400`() {
        val budgetId = createBudgetViaJdbc("2026-05")
        val paymentMethodId = firstPaymentMethodId()

        val body = """
            {
              "periods": [
                { "paymentMethodId": $paymentMethodId, "startDate": "2026-05-10", "endDate": "2026-04-10" }
              ]
            }
        """.trimIndent()

        mockMvc.perform(
            put("/budgets/$budgetId/periods")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
        ).andExpect(status().isBadRequest)
    }

    // --- Helpers ---

    private fun createCategory(name: String, icon: String?): Long {
        jdbcTemplate.update(
            "INSERT INTO categories (name, icon, group_id) VALUES (?, ?, 1)",
            name, icon
        )
        return jdbcTemplate.queryForList(
            "SELECT id FROM categories WHERE name = ?",
            name
        ).first()["ID"] as Long
    }

    private fun createBudgetViaJdbc(yearMonth: String): Long {
        jdbcTemplate.update("INSERT INTO budgets (reference_month, group_id) VALUES (?, 1)", yearMonth)
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

    private fun firstPaymentMethodId(): Long {
        val existing = jdbcTemplate.queryForList(
            "SELECT id FROM payment_methods LIMIT 1"
        ).firstOrNull()?.get("ID") as? Long
        if (existing != null) return existing

        val holderId = jdbcTemplate.queryForList(
            "SELECT id FROM holders LIMIT 1"
        ).firstOrNull()?.get("ID") as? Long ?: run {
            jdbcTemplate.update("INSERT INTO holders (name, group_id) VALUES ('Test Holder', 1)")
            jdbcTemplate.queryForObject("SELECT id FROM holders LIMIT 1", Long::class.java)!!
        }
        jdbcTemplate.update(
            "INSERT INTO payment_methods (name, type, holder_id, closing_day, group_id) VALUES ('Nubank', 'CREDIT_CARD', ?, ?, 1)",
            holderId, 10
        )
        return jdbcTemplate.queryForObject("SELECT id FROM payment_methods LIMIT 1", Long::class.java)!!
    }

    private fun createPaymentMethodViaJdbc(name: String, closingDay: Int): Long {
        val holderId = jdbcTemplate.queryForList(
            "SELECT id FROM holders LIMIT 1"
        ).firstOrNull()?.get("ID") as? Long ?: run {
            jdbcTemplate.update("INSERT INTO holders (name, group_id) VALUES ('Test Holder', 1)")
            jdbcTemplate.queryForObject("SELECT id FROM holders LIMIT 1", Long::class.java)!!
        }
        jdbcTemplate.update(
            "INSERT INTO payment_methods (name, type, holder_id, closing_day, group_id) VALUES (?, 'CREDIT_CARD', ?, ?, 1)",
            name, holderId, closingDay
        )
        return jdbcTemplate.queryForList(
            "SELECT id FROM payment_methods WHERE name = ?", name
        ).first()["ID"] as Long
    }

    private fun insertPeriod(budgetId: Long, paymentMethodId: Long, startDate: String, endDate: String) {
        jdbcTemplate.update(
            "INSERT INTO budget_payment_periods (budget_id, payment_method_id, start_date, end_date) VALUES (?, ?, ?, ?)",
            budgetId, paymentMethodId, startDate, endDate
        )
    }
}
