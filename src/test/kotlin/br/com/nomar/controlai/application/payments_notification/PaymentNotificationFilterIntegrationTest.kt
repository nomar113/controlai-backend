package br.com.nomar.controlai.application.payments_notification

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
class PaymentNotificationFilterIntegrationTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach
    fun cleanUp() {
        jdbcTemplate.update("DELETE FROM installments")
        jdbcTemplate.update("DELETE FROM payment_notifications")
    }

    @Test
    fun `GET notifications with month param returns only notifications from that month`() {
        insertNotification("2026-05-10 14:00:00", "Store May 1", 100.00)
        insertNotification("2026-05-20 10:00:00", "Store May 2", 200.00)
        insertNotification("2026-04-15 09:00:00", "Store April", 50.00)
        insertNotification("2026-06-01 08:00:00", "Store June", 75.00)

        mockMvc.perform(get("/payments/notifications").param("month", "2026-05"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content.length()").value(2))
            .andExpect(jsonPath("$.content[0].merchantName").value("Store May 2"))
            .andExpect(jsonPath("$.content[1].merchantName").value("Store May 1"))
            .andExpect(jsonPath("$.totalElements").value(2))
    }

    @Test
    fun `GET notifications without month param returns current month notifications`() {
        val now = java.time.LocalDateTime.now()
        val currentTimestamp = now.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        val lastMonth = now.minusMonths(1).format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))

        insertNotification(currentTimestamp, "Store Current", 100.00)
        insertNotification(lastMonth, "Store Last Month", 50.00)

        mockMvc.perform(get("/payments/notifications"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content.length()").value(1))
            .andExpect(jsonPath("$.content[0].merchantName").value("Store Current"))
    }

    @Test
    fun `GET notifications with invalid month format returns 400`() {
        mockMvc.perform(get("/payments/notifications").param("month", "invalid"))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `GET notifications with pagination returns correct page`() {
        for (i in 1..5) {
            insertNotification("2026-05-${10 + i} 10:00:00", "Store $i", i * 10.0)
        }

        mockMvc.perform(
            get("/payments/notifications")
                .param("month", "2026-05")
                .param("page", "0")
                .param("size", "2")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content.length()").value(2))
            .andExpect(jsonPath("$.totalElements").value(5))
            .andExpect(jsonPath("$.totalPages").value(3))
            .andExpect(jsonPath("$.number").value(0))
            .andExpect(jsonPath("$.size").value(2))
            .andExpect(jsonPath("$.last").value(false))

        mockMvc.perform(
            get("/payments/notifications")
                .param("month", "2026-05")
                .param("page", "2")
                .param("size", "2")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content.length()").value(1))
            .andExpect(jsonPath("$.last").value(true))
    }

    @Test
    fun `GET notifications for month with no data returns empty page`() {
        insertNotification("2026-05-10 14:00:00", "Store May", 100.00)

        mockMvc.perform(get("/payments/notifications").param("month", "2026-01"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content.length()").value(0))
            .andExpect(jsonPath("$.totalElements").value(0))
    }

    @Test
    fun `GET notifications respects soft delete`() {
        insertNotification("2026-05-10 14:00:00", "Active Store", 100.00)
        insertDeletedNotification("2026-05-15 14:00:00", "Deleted Store", 200.00)

        mockMvc.perform(get("/payments/notifications").param("month", "2026-05"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content.length()").value(1))
            .andExpect(jsonPath("$.content[0].merchantName").value("Active Store"))
    }

    @Test
    fun `GET notifications are ordered by purchasedAt DESC`() {
        insertNotification("2026-05-01 08:00:00", "First", 10.00)
        insertNotification("2026-05-15 12:00:00", "Middle", 20.00)
        insertNotification("2026-05-28 18:00:00", "Last", 30.00)

        mockMvc.perform(get("/payments/notifications").param("month", "2026-05"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content[0].merchantName").value("Last"))
            .andExpect(jsonPath("$.content[1].merchantName").value("Middle"))
            .andExpect(jsonPath("$.content[2].merchantName").value("First"))
    }

    // --- Helpers ---

    private fun insertNotification(purchasedAt: String, merchantName: String, amount: Double) {
        jdbcTemplate.update(
            """INSERT INTO payment_notifications
               (card_last_digits, purchased_at, amount, merchant_name, number_of_installments, origin, origin_type)
               VALUES (?, ?, ?, ?, ?, ?, ?)""",
            "1234", purchasedAt, amount, merchantName, 1, "MANUAL", "MANUAL"
        )
    }

    private fun insertDeletedNotification(purchasedAt: String, merchantName: String, amount: Double) {
        jdbcTemplate.update(
            """INSERT INTO payment_notifications
               (card_last_digits, purchased_at, amount, merchant_name, number_of_installments, origin, origin_type, deleted_at)
               VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)""",
            "1234", purchasedAt, amount, merchantName, 1, "MANUAL", "MANUAL"
        )
    }
}
