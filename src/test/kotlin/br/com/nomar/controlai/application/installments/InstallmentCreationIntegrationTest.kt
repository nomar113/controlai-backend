package br.com.nomar.controlai.application.installments

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
class InstallmentCreationIntegrationTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var jdbcTemplate: JdbcTemplate

    private var paymentMethodId: Long = 0
    private var categoryId: Long = 0

    @BeforeEach
    fun cleanUp() {
        jdbcTemplate.update("DELETE FROM installments")
        jdbcTemplate.update("UPDATE payment_notifications SET category_id = NULL, payment_method_id = NULL, sub_card_id = NULL")
        jdbcTemplate.update("DELETE FROM payment_notifications")
        jdbcTemplate.update("DELETE FROM budget_payment_periods")
        jdbcTemplate.update("DELETE FROM budget_incomes")
        jdbcTemplate.update("DELETE FROM budget_items")
        jdbcTemplate.update("DELETE FROM budgets")
        jdbcTemplate.update("DELETE FROM categories")
        jdbcTemplate.update("DELETE FROM sub_cards")
        jdbcTemplate.update("DELETE FROM payment_methods")
        jdbcTemplate.update("DELETE FROM holders")

        jdbcTemplate.update("INSERT INTO holders (name, group_id) VALUES ('Test Holder', 1)")
        val holderId = jdbcTemplate.queryForObject("SELECT id FROM holders WHERE name = 'Test Holder'", Long::class.java)!!

        jdbcTemplate.update("INSERT INTO payment_methods (name, type, holder_id, group_id) VALUES ('Nubank', 'CREDIT_CARD', ?, 1)", holderId)
        paymentMethodId = jdbcTemplate.queryForObject("SELECT id FROM payment_methods WHERE name = 'Nubank'", Long::class.java)!!

        jdbcTemplate.update("INSERT INTO categories (name, group_id) VALUES ('Tecnologia', 1)")
        categoryId = jdbcTemplate.queryForObject("SELECT id FROM categories WHERE name = 'Tecnologia'", Long::class.java)!!
    }

    @AfterEach
    fun tearDown() {
        // POST /notifications/manual with numberOfInstallments > 1 now auto-creates budgets via
        // EnsureFutureBudgetProvider (Task 3.0); without this, orphaned budget_payment_periods
        // rows referencing this suite's payment_methods break other suites' cleanup by FK.
        jdbcTemplate.update("DELETE FROM budget_payment_periods")
        jdbcTemplate.update("DELETE FROM budget_incomes")
        jdbcTemplate.update("DELETE FROM budget_items")
        jdbcTemplate.update("DELETE FROM budgets")
    }

    @Test
    fun `POST with numberOfInstallments 5 should create 5 installment records`() {
        mockMvc.perform(
            post("/payments/notifications/manual")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                        "merchantName": "Apple Store",
                        "amount": 589.90,
                        "purchasedAt": "2026-05-15T10:00:00",
                        "paymentMethodId": $paymentMethodId,
                        "categoryId": $categoryId,
                        "numberOfInstallments": 5
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.numberOfInstallments").value(5))
            .andExpect(jsonPath("$.installments.length()").value(5))
            .andExpect(jsonPath("$.installments[0].installmentNumber").value(1))
            .andExpect(jsonPath("$.installments[4].installmentNumber").value(5))
            .andExpect(jsonPath("$.installments[0].dueDate").value("2026-05-15"))
            .andExpect(jsonPath("$.installments[1].dueDate").value("2026-06-15"))

        val count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM installments", Int::class.java)
        assert(count == 5) { "Expected 5 installments in DB but found $count" }
    }

    @Test
    fun `POST with numberOfInstallments 1 should create a single installment record but omit it from the response`() {
        mockMvc.perform(
            post("/payments/notifications/manual")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                        "merchantName": "Padaria",
                        "amount": 15.00,
                        "purchasedAt": "2026-05-15T10:00:00",
                        "paymentMethodId": $paymentMethodId,
                        "categoryId": $categoryId,
                        "numberOfInstallments": 1
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.installments").isEmpty)

        val count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM installments", Int::class.java)
        assert(count == 1) { "Expected 1 installment in DB but found $count" }
    }

    @Test
    fun `POST with invalid categoryId should not create any records`() {
        mockMvc.perform(
            post("/payments/notifications/manual")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                        "merchantName": "Loja X",
                        "amount": 100.00,
                        "purchasedAt": "2026-05-15T10:00:00",
                        "paymentMethodId": $paymentMethodId,
                        "categoryId": 99999,
                        "numberOfInstallments": 3
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isBadRequest)

        val notifCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM payment_notifications", Int::class.java)
        val installCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM installments", Int::class.java)
        assertEquals(0, notifCount, "Expected 0 notifications")
        assertEquals(0, installCount, "Expected 0 installments")
    }
}
