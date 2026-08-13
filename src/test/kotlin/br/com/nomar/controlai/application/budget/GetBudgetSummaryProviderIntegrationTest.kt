package br.com.nomar.controlai.application.budget

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.AfterEach
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
import java.math.BigDecimal
import kotlin.test.assertEquals

// Real-MySQL companion to GetBudgetSummaryProviderTest: proves the native SQL added by
// Tarefa 4.0 (installments JOIN + CASE WHEN in BudgetPeriodSqlSupport) actually aggregates
// correctly against a real database, which a mocked JdbcTemplate cannot verify. Goes through
// GET /budgets (MockMvc/DispatcherServlet) rather than calling GetBudgetSummaryProvider
// directly, since BudgetModel.items/incomes are lazy collections that only stay attached to a
// Hibernate session for the lifetime of a real request (Open Session In View).
@SpringBootTest
@AutoConfigureMockMvc
class GetBudgetSummaryProviderIntegrationTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var jdbcTemplate: JdbcTemplate
    @Autowired private lateinit var objectMapper: ObjectMapper

    private val groupId = 1L
    private var holderId: Long = 0
    private var categoryId: Long = 0

    @BeforeEach
    fun setUp() {
        cleanAll()
        jdbcTemplate.update("INSERT INTO holders (name, group_id) VALUES ('Titular Teste', ?)", groupId)
        holderId = jdbcTemplate.queryForObject("SELECT id FROM holders WHERE name = 'Titular Teste'", Long::class.java)!!
        jdbcTemplate.update("INSERT INTO categories (name, group_id) VALUES ('Compras', ?)", groupId)
        categoryId = jdbcTemplate.queryForObject("SELECT id FROM categories WHERE name = 'Compras'", Long::class.java)!!
    }

    @AfterEach
    fun tearDown() {
        cleanAll()
    }

    private fun cleanAll() {
        jdbcTemplate.update("DELETE FROM installments")
        jdbcTemplate.update("UPDATE payment_notifications SET category_id = NULL, payment_method_id = NULL, sub_card_id = NULL")
        jdbcTemplate.update("DELETE FROM payment_notifications")
        jdbcTemplate.update("DELETE FROM budget_payment_periods")
        jdbcTemplate.update("DELETE FROM budget_incomes")
        jdbcTemplate.update("DELETE FROM budget_items")
        jdbcTemplate.update("DELETE FROM budgets")
        jdbcTemplate.update("DELETE FROM categories")
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

    private fun insertBudget(yearMonth: String): Long {
        jdbcTemplate.update("INSERT INTO budgets (reference_month, group_id) VALUES (?, ?)", yearMonth, groupId)
        return jdbcTemplate.queryForObject(
            "SELECT id FROM budgets WHERE reference_month = ? AND group_id = ?", Long::class.java, yearMonth, groupId,
        )!!
    }

    private fun insertPeriod(budgetId: Long, paymentMethodId: Long, startDate: String, endDate: String) {
        jdbcTemplate.update(
            "INSERT INTO budget_payment_periods (budget_id, payment_method_id, start_date, end_date) VALUES (?, ?, ?, ?)",
            budgetId, paymentMethodId, startDate, endDate,
        )
    }

    private fun insertBudgetItem(budgetId: Long, categoryId: Long, expected: BigDecimal) {
        jdbcTemplate.update(
            "INSERT INTO budget_items (budget_id, category_id, type, expected) VALUES (?, ?, 'EXPENSE', ?)",
            budgetId, categoryId, expected,
        )
    }

    private fun insertNotification(
        merchantName: String,
        amount: BigDecimal,
        purchasedAt: String,
        numberOfInstallments: Int,
        paymentMethodId: Long,
        categoryId: Long = this.categoryId,
    ): Long {
        jdbcTemplate.update(
            """INSERT INTO payment_notifications
               (group_id, purchased_at, amount, merchant_name, number_of_installments, origin, origin_type, category_id, payment_method_id)
               VALUES (?, ?, ?, ?, ?, 'MANUAL', 'MANUAL', ?, ?)""",
            groupId, purchasedAt, amount, merchantName, numberOfInstallments, categoryId, paymentMethodId,
        )
        return jdbcTemplate.queryForObject(
            "SELECT id FROM payment_notifications WHERE merchant_name = ?", Long::class.java, merchantName,
        )!!
    }

    private fun insertInstallment(parentId: Long, number: Int, total: Int, amount: BigDecimal, dueDate: String) {
        jdbcTemplate.update(
            """INSERT INTO installments (group_id, parent_id, installment_number, total_installments, amount, due_date)
               VALUES (?, ?, ?, ?, ?, ?)""",
            groupId, parentId, number, total, amount, dueDate,
        )
    }

    @Test
    fun `GET budgets sums only the installment due in that month, not the purchase total`() {
        val paymentMethodId = insertCreditCard(closingDay = 10)

        // 3x purchase on Jan 15 (after the 10th closing day): installments bill Feb/Mar/Apr.
        val parentId = insertNotification("Apple Store", BigDecimal("300.00"), "2026-01-15 10:00:00", 3, paymentMethodId)
        insertInstallment(parentId, 1, 3, BigDecimal("100.00"), "2026-02-15")
        insertInstallment(parentId, 2, 3, BigDecimal("100.00"), "2026-03-15")
        insertInstallment(parentId, 3, 3, BigDecimal("100.00"), "2026-04-15")

        val februaryBudgetId = insertBudget("2026-02")
        insertPeriod(februaryBudgetId, paymentMethodId, "2026-01-11", "2026-02-10")
        insertBudgetItem(februaryBudgetId, categoryId, BigDecimal("500.00"))

        mockMvc.perform(get("/budgets?month=2026-02"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalActual").value(100.00))
            .andExpect(jsonPath("$.items[0].actual").value(100.00))
            .andExpect(jsonPath("$.paymentMethodTotals[0].total").value(100.00))
    }

    @Test
    fun `GET budgets for March counts only the March installment, not a duplicate of February's`() {
        val paymentMethodId = insertCreditCard(closingDay = 10)

        val parentId = insertNotification("Apple Store", BigDecimal("300.00"), "2026-01-15 10:00:00", 3, paymentMethodId)
        insertInstallment(parentId, 1, 3, BigDecimal("100.00"), "2026-02-15")
        insertInstallment(parentId, 2, 3, BigDecimal("100.00"), "2026-03-15")
        insertInstallment(parentId, 3, 3, BigDecimal("100.00"), "2026-04-15")

        val marchBudgetId = insertBudget("2026-03")
        insertPeriod(marchBudgetId, paymentMethodId, "2026-02-11", "2026-03-10")
        insertBudgetItem(marchBudgetId, categoryId, BigDecimal("500.00"))

        mockMvc.perform(get("/budgets?month=2026-03"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalActual").value(100.00))
    }

    @Test
    fun `GET budgets still sums the full amount for a cash purchase, unaffected by the installments JOIN`() {
        val paymentMethodId = insertCreditCard(closingDay = 10)
        insertNotification("Padaria", BigDecimal("45.00"), "2026-01-20 10:00:00", 1, paymentMethodId)

        val februaryBudgetId = insertBudget("2026-02")
        insertPeriod(februaryBudgetId, paymentMethodId, "2026-01-11", "2026-02-10")
        insertBudgetItem(februaryBudgetId, categoryId, BigDecimal("200.00"))

        mockMvc.perform(get("/budgets?month=2026-02"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalActual").value(45.00))
    }

    @Test
    fun `GET budgets does not duplicate or drop totals when multiple parceled purchases fan out through the installments JOIN in the same month`() {
        val paymentMethodId = insertCreditCard(closingDay = 10)
        jdbcTemplate.update("INSERT INTO categories (name, group_id) VALUES ('Servicos', ?)", groupId)
        val servicesCategoryId = jdbcTemplate.queryForObject(
            "SELECT id FROM categories WHERE name = 'Servicos'", Long::class.java,
        )!!

        // Compras: two overlapping parceladas (3x and 2x) fan out to 5 installment rows total
        // via the LEFT JOIN, plus one cash purchase — must collapse to 100 + 100 + 50 = 250.00.
        val purchaseA = insertNotification("Compra 3x", BigDecimal("300.00"), "2026-01-15 10:00:00", 3, paymentMethodId)
        insertInstallment(purchaseA, 1, 3, BigDecimal("100.00"), "2026-02-15")
        insertInstallment(purchaseA, 2, 3, BigDecimal("100.00"), "2026-03-15")
        insertInstallment(purchaseA, 3, 3, BigDecimal("100.00"), "2026-04-15")
        val purchaseB = insertNotification("Compra 2x", BigDecimal("200.00"), "2026-01-16 10:00:00", 2, paymentMethodId)
        insertInstallment(purchaseB, 1, 2, BigDecimal("100.00"), "2026-02-16")
        insertInstallment(purchaseB, 2, 2, BigDecimal("100.00"), "2026-03-16")
        insertNotification("Compra a vista Compras", BigDecimal("50.00"), "2026-01-20 10:00:00", 1, paymentMethodId)

        // Servicos: one cash purchase + one 4x parcelada fans out to 4 more installment rows —
        // must not leak into Compras' total, and must collapse to 80 + 50 = 130.00.
        insertNotification("Compra a vista Servicos", BigDecimal("80.00"), "2026-01-22 10:00:00", 1, paymentMethodId, categoryId = servicesCategoryId)
        val purchaseE = insertNotification("Compra 4x", BigDecimal("200.00"), "2026-01-18 10:00:00", 4, paymentMethodId, categoryId = servicesCategoryId)
        insertInstallment(purchaseE, 1, 4, BigDecimal("50.00"), "2026-02-18")
        insertInstallment(purchaseE, 2, 4, BigDecimal("50.00"), "2026-03-18")
        insertInstallment(purchaseE, 3, 4, BigDecimal("50.00"), "2026-04-18")
        insertInstallment(purchaseE, 4, 4, BigDecimal("50.00"), "2026-05-18")

        val februaryBudgetId = insertBudget("2026-02")
        insertPeriod(februaryBudgetId, paymentMethodId, "2026-01-11", "2026-02-10")
        insertBudgetItem(februaryBudgetId, categoryId, BigDecimal("500.00"))
        insertBudgetItem(februaryBudgetId, servicesCategoryId, BigDecimal("300.00"))

        val response = mockMvc.perform(get("/budgets?month=2026-02"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalActual").value(380.00))
            .andExpect(jsonPath("$.paymentMethodTotals[0].total").value(380.00))
            .andReturn()

        val items = objectMapper.readTree(response.response.contentAsString)["items"]
        val actualByCategoryId = items.associate { it["categoryId"].asLong() to it["actual"].asDouble() }
        assertEquals(250.00, actualByCategoryId[categoryId])
        assertEquals(130.00, actualByCategoryId[servicesCategoryId])
    }
}
