package br.com.nomar.controlai.application.payments_notification

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
class CancelPaymentNotificationControllerTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach
    fun cleanUp() {
        jdbcTemplate.update("DELETE FROM installments")
        jdbcTemplate.update("DELETE FROM payment_notifications")
    }

    private fun insertNotification(cancelledAt: String? = null): Long {
        jdbcTemplate.update(
            """INSERT INTO payment_notifications (card_last_digits, purchased_at, amount, merchant_name, number_of_installments, origin, origin_type, cancelled_at)
               VALUES ('1234', '2024-01-15 10:00:00', 99.90, 'Loja Test', 1, 'NUBANK', 'HTTP_REQUEST', ${if (cancelledAt != null) "'$cancelledAt'" else "NULL"})"""
        )
        return jdbcTemplate.queryForObject("SELECT MAX(id) FROM payment_notifications", Long::class.java)!!
    }

    @Test
    fun `PATCH cancel should return 200 for valid notification`() {
        val id = insertNotification()

        mockMvc.perform(patch("/payments/notifications/$id/cancel"))
            .andExpect(status().isOk)
    }

    @Test
    fun `PATCH cancel should return 404 for non-existent notification`() {
        mockMvc.perform(patch("/payments/notifications/999999/cancel"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `PATCH cancel should return 409 for already cancelled notification`() {
        val id = insertNotification(cancelledAt = "2024-01-20 10:00:00")

        mockMvc.perform(patch("/payments/notifications/$id/cancel"))
            .andExpect(status().isConflict)
    }
}
