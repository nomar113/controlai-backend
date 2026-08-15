package br.com.nomar.controlai.application.purchases

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
class PurchaseCategoryIntegrationTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper
    @Autowired private lateinit var jdbcTemplate: JdbcTemplate

    private var categoryId: Long = 0
    private var paymentMethodId: Long = 0

    @BeforeEach
    fun cleanUp() {
        jdbcTemplate.update("DELETE FROM installments")
        jdbcTemplate.update("UPDATE payment_notifications SET category_id = NULL, payment_method_id = NULL, sub_card_id = NULL")
        jdbcTemplate.update("DELETE FROM payment_notifications")
        // Every payment_notifications row now also gets a budget via EnsureFutureBudgetProvider
        // (Task 1.0), which references payment_methods through budget_payment_periods.
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

        jdbcTemplate.update("INSERT INTO categories (name, group_id) VALUES ('Mercado', 1)")
        categoryId = jdbcTemplate.queryForObject("SELECT id FROM categories WHERE name = 'Mercado'", Long::class.java)!!
    }

    @Test
    fun `POST manual notification with categoryId should persist correctly`() {
        mockMvc.perform(
            post("/payments/notifications/manual")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                        "merchantName": "Supermercado ABC",
                        "amount": 150.00,
                        "purchasedAt": "2026-04-10T10:00:00",
                        "paymentMethodId": $paymentMethodId,
                        "categoryId": $categoryId,
                        "numberOfInstallments": 1
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.categoryId").value(categoryId))
            .andExpect(jsonPath("$.merchantName").value("Supermercado ABC"))
    }

    @Test
    fun `GET purchases should return categoryName from JOIN`() {
        jdbcTemplate.update(
            """INSERT INTO payment_notifications
               (purchased_at, amount, merchant_name, number_of_installments, origin, origin_type, category_id, group_id)
               VALUES (CURRENT_TIMESTAMP, 100.00, 'Loja Teste', 1, 'MANUAL', 'MANUAL', ?, 1)""",
            categoryId
        )

        mockMvc.perform(get("/purchases"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].categoryName").value("Mercado"))
    }

    @Test
    fun `GET purchases without category should return categoryName null`() {
        jdbcTemplate.update(
            """INSERT INTO payment_notifications
               (purchased_at, amount, merchant_name, number_of_installments, origin, origin_type, group_id)
               VALUES (CURRENT_TIMESTAMP, 50.00, 'Loja Sem Categoria', 1, 'MANUAL', 'MANUAL', 1)"""
        )

        mockMvc.perform(get("/purchases"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].categoryName").isEmpty)
    }

    @Test
    fun `POST manual notification without categoryId should return 201`() {
        mockMvc.perform(
            post("/payments/notifications/manual")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                        "merchantName": "Loja X",
                        "amount": 50.00,
                        "purchasedAt": "2026-04-10T10:00:00",
                        "paymentMethodId": $paymentMethodId,
                        "numberOfInstallments": 1
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isCreated)
    }
}
