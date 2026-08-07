package br.com.nomar.controlai.application.payment_methods

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
import java.math.BigDecimal
import kotlin.test.assertEquals

@SpringBootTest
@AutoConfigureMockMvc
class SummaryIntegrationTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper
    @Autowired private lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach
    fun cleanUp() {
        jdbcTemplate.update("DELETE FROM installments")
        jdbcTemplate.update("UPDATE payment_notifications SET payment_method_id = NULL, sub_card_id = NULL")
        jdbcTemplate.update("DELETE FROM budget_payment_periods")
        jdbcTemplate.update("DELETE FROM budgets")
        jdbcTemplate.update("DELETE FROM sub_cards")
        jdbcTemplate.update("DELETE FROM payment_methods")
        jdbcTemplate.update("DELETE FROM holders")
        jdbcTemplate.update("DELETE FROM payment_notifications")
    }

    @Test
    fun `GET summary should return totals per card and sub-card`() {
        val holderId = createHolder("Ramon")
        val pmId = createPaymentMethod(holderId, "Smiles Infinite", "CREDIT_CARD")
        val subCardId = createSubCard(pmId, "6668", "PHYSICAL_HOLDER")

        insertExpense(pmId, subCardId, "2025-03-10 14:00:00", BigDecimal("150.00"))
        insertExpense(pmId, subCardId, "2025-03-15 10:00:00", BigDecimal("250.00"))

        mockMvc.perform(get("/payment-methods/summary").param("month", "2025-03"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].paymentMethodId").value(pmId))
            .andExpect(jsonPath("$[0].name").value("Smiles Infinite"))
            .andExpect(jsonPath("$[0].holderName").value("Ramon"))
            .andExpect(jsonPath("$[0].totalSpent").value(400.00))
            .andExpect(jsonPath("$[0].subCardTotals.length()").value(1))
            .andExpect(jsonPath("$[0].subCardTotals[0].subCardId").value(subCardId))
            .andExpect(jsonPath("$[0].subCardTotals[0].lastFourDigits").value("6668"))
            .andExpect(jsonPath("$[0].subCardTotals[0].total").value(400.00))
    }

    @Test
    fun `GET summary should return card with zero total when no expenses`() {
        val holderId = createHolder("Aline")
        createPaymentMethod(holderId, "Nubank", "CREDIT_CARD")

        mockMvc.perform(get("/payment-methods/summary").param("month", "2025-03"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].name").value("Nubank"))
            .andExpect(jsonPath("$[0].totalSpent").value(0))
    }

    @Test
    fun `GET summary should not mix expenses from different months`() {
        val holderId = createHolder("Ramon")
        val pmId = createPaymentMethod(holderId, "Smiles", "CREDIT_CARD")
        val subCardId = createSubCard(pmId, "6668", "PHYSICAL_HOLDER")

        insertExpense(pmId, subCardId, "2025-03-10 14:00:00", BigDecimal("100.00"))
        insertExpense(pmId, subCardId, "2025-04-05 10:00:00", BigDecimal("200.00"))

        mockMvc.perform(get("/payment-methods/summary").param("month", "2025-03"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].totalSpent").value(100.00))

        mockMvc.perform(get("/payment-methods/summary").param("month", "2025-04"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].totalSpent").value(200.00))
    }

    @Test
    fun `GET summary sub-card totals should sum to parent card total`() {
        val holderId = createHolder("Ramon")
        val pmId = createPaymentMethod(holderId, "Smiles", "CREDIT_CARD")
        val sc1 = createSubCard(pmId, "6668", "PHYSICAL_HOLDER")
        val sc2 = createSubCard(pmId, "9687", "PHYSICAL_DEPENDENT")

        insertExpense(pmId, sc1, "2025-03-10 14:00:00", BigDecimal("300.00"))
        insertExpense(pmId, sc2, "2025-03-12 10:00:00", BigDecimal("150.00"))
        insertExpense(pmId, sc2, "2025-03-20 18:00:00", BigDecimal("50.00"))

        mockMvc.perform(get("/payment-methods/summary").param("month", "2025-03"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].totalSpent").value(500.00))
            .andExpect(jsonPath("$[0].subCardTotals.length()").value(2))
    }

    @Test
    fun `GET summary for a month without an existing budget computes totals without creating one`() {
        val holderId = createHolder("Ramon")
        val pmId = createPaymentMethod(holderId, "Smiles Infinite", "CREDIT_CARD")
        insertExpense(pmId, null, "2026-09-10 14:00:00", BigDecimal("150.00"))

        mockMvc.perform(get("/payment-methods/summary").param("month", "2026-09"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].totalSpent").value(150.00))

        val budgetCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM budgets WHERE reference_month = ?", Long::class.java, "2026-09"
        )
        assertEquals(0L, budgetCount)
    }

    @Test
    fun `GET summary without month parameter should return 400`() {
        mockMvc.perform(get("/payment-methods/summary"))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `GET summary with invalid month format should return 400`() {
        mockMvc.perform(get("/payment-methods/summary").param("month", "invalid"))
            .andExpect(status().isBadRequest)
    }

    // --- Helpers ---

    private fun createHolder(name: String): Long {
        val response = mockMvc.perform(
            post("/holders")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name": "$name"}""")
        ).andReturn().response.contentAsString
        return objectMapper.readTree(response).get("id").asLong()
    }

    private fun createPaymentMethod(holderId: Long, name: String, type: String): Long {
        val response = mockMvc.perform(
            post("/payment-methods")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name": "$name", "type": "$type", "holderId": $holderId}""")
        ).andReturn().response.contentAsString
        return objectMapper.readTree(response).get("id").asLong()
    }

    private fun createSubCard(pmId: Long, lastFour: String, type: String): Long {
        val response = mockMvc.perform(
            post("/payment-methods/$pmId/sub-cards")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"lastFourDigits": "$lastFour", "type": "$type"}""")
        ).andReturn().response.contentAsString
        return objectMapper.readTree(response).get("id").asLong()
    }

    private fun insertExpense(pmId: Long, subCardId: Long?, purchasedAt: String, amount: BigDecimal) {
        jdbcTemplate.update(
            """
            INSERT INTO payment_notifications (card_last_digits, purchased_at, amount, merchant_name,
                number_of_installments, origin, origin_type, payment_method_id, sub_card_id, group_id)
            VALUES ('0000', ?, ?, 'Test Store', 1, 'NUBANK', 'HTTP_REQUEST', ?, ?, 1)
            """.trimIndent(),
            purchasedAt,
            amount,
            pmId,
            subCardId,
        )
    }
}
