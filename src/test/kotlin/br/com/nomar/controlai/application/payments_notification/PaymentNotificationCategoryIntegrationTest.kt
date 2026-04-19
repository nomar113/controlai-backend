package br.com.nomar.controlai.application.payments_notification

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
class PaymentNotificationCategoryIntegrationTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper
    @Autowired private lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach
    fun cleanUp() {
        jdbcTemplate.update("DELETE FROM budget_items")
        jdbcTemplate.update("DELETE FROM budgets")
        jdbcTemplate.update("UPDATE payment_notifications SET category_id = NULL, category = NULL")
        jdbcTemplate.update("DELETE FROM payment_notifications WHERE merchant_name = 'Test Store Category'")
        jdbcTemplate.update("UPDATE purchase_invoices SET category_id = NULL")
        jdbcTemplate.update("DELETE FROM categories")
    }

    @Test
    fun `PATCH category with valid categoryId should return 200 with updated notification`() {
        val categoryId = createCategory("Mercado")
        val notificationId = createNotification()

        mockMvc.perform(
            patch("/payments/notifications/$notificationId/category")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"categoryId": $categoryId}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.categoryId").value(categoryId))
            .andExpect(jsonPath("$.category").value("Mercado"))
    }

    @Test
    fun `PATCH category with null categoryId should clear category fields`() {
        val categoryId = createCategory("Mercado")
        val notificationId = createNotification()

        // First set a category
        mockMvc.perform(
            patch("/payments/notifications/$notificationId/category")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"categoryId": $categoryId}""")
        )
            .andExpect(status().isOk)

        // Then remove it
        mockMvc.perform(
            patch("/payments/notifications/$notificationId/category")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"categoryId": null}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.categoryId").doesNotExist())
            .andExpect(jsonPath("$.category").doesNotExist())
    }

    @Test
    fun `PATCH category with non-existent categoryId should return 400`() {
        val notificationId = createNotification()

        mockMvc.perform(
            patch("/payments/notifications/$notificationId/category")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"categoryId": 99999}""")
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `PATCH category with non-existent notification id should return 404`() {
        val categoryId = createCategory("Mercado")

        mockMvc.perform(
            patch("/payments/notifications/99999/category")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"categoryId": $categoryId}""")
        )
            .andExpect(status().isNotFound)
    }

    @Test
    fun `PATCH category should sync legacy category string field`() {
        val categoryId = createCategory("Alimentacao")
        val notificationId = createNotification()

        mockMvc.perform(
            patch("/payments/notifications/$notificationId/category")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"categoryId": $categoryId}""")
        )
            .andExpect(status().isOk)

        // Verify via GET
        mockMvc.perform(get("/payments/notifications/$notificationId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.categoryId").value(categoryId))
            .andExpect(jsonPath("$.category").value("Alimentacao"))
    }

    @Test
    fun `full flow - create notification then categorize then verify`() {
        val categoryId = createCategory("Transporte")
        val notificationId = createNotification()

        // Verify no category initially
        mockMvc.perform(get("/payments/notifications/$notificationId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.categoryId").doesNotExist())
            .andExpect(jsonPath("$.category").doesNotExist())

        // Categorize
        mockMvc.perform(
            patch("/payments/notifications/$notificationId/category")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"categoryId": $categoryId}""")
        )
            .andExpect(status().isOk)

        // Verify category set
        mockMvc.perform(get("/payments/notifications/$notificationId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.categoryId").value(categoryId))
            .andExpect(jsonPath("$.category").value("Transporte"))

        // Remove category
        mockMvc.perform(
            patch("/payments/notifications/$notificationId/category")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"categoryId": null}""")
        )
            .andExpect(status().isOk)

        // Verify category cleared
        mockMvc.perform(get("/payments/notifications/$notificationId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.categoryId").doesNotExist())
            .andExpect(jsonPath("$.category").doesNotExist())
    }

    // --- Helpers ---

    private fun createCategory(name: String): Long {
        val response = mockMvc.perform(
            post("/categories")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name": "$name"}""")
        ).andReturn().response.contentAsString

        return objectMapper.readTree(response).get("id").asLong()
    }

    private fun createNotification(): Long {
        jdbcTemplate.update(
            """INSERT INTO payment_notifications
               (card_last_digits, purchased_at, amount, merchant_name, number_of_installments, origin, origin_type)
               VALUES (?, CURRENT_TIMESTAMP, ?, ?, ?, ?, ?)""",
            "9999", 50.00, "Test Store Category", 1, "MANUAL", "MANUAL"
        )
        return jdbcTemplate.queryForObject(
            "SELECT id FROM payment_notifications WHERE merchant_name = 'Test Store Category' ORDER BY id DESC LIMIT 1",
            Long::class.java
        )!!
    }
}
