package br.com.nomar.controlai.application.payments_notification

import br.com.nomar.controlai.application.payments_notification.entrypoint.rest.request.PurchasedAtDeserializer
import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * Tarefa 4.2/4.3: valida o contrato de `purchasedAt` nos endpoints que recebem data/hora do
 * cliente — string ISO-8601 com offset explicito e o fallback de compatibilidade (string sem
 * offset, interpretada em America/Sao_Paulo com log WARN) para os endpoints
 * POST /payments/notifications/manual e PATCH /payments/notifications/{id}/purchased-at.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PurchasedAtIntegrationTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var jdbcTemplate: JdbcTemplate

    private lateinit var appender: ListAppender<ILoggingEvent>

    @BeforeEach
    fun setUp() {
        jdbcTemplate.update("DELETE FROM installments")
        jdbcTemplate.update("DELETE FROM payment_notifications")
        jdbcTemplate.update("DELETE FROM payment_methods")
        jdbcTemplate.update("DELETE FROM holders")

        appender = ListAppender()
        appender.start()
        (LoggerFactory.getLogger(PurchasedAtDeserializer::class.java) as Logger).addAppender(appender)
    }

    @AfterEach
    fun tearDown() {
        (LoggerFactory.getLogger(PurchasedAtDeserializer::class.java) as Logger).detachAppender(appender)
    }

    private fun insertHolder(): Long {
        jdbcTemplate.update("INSERT INTO holders (name, group_id) VALUES ('Titular Teste', 1)")
        return jdbcTemplate.queryForObject("SELECT MAX(id) FROM holders", Long::class.java)!!
    }

    private fun insertPaymentMethod(holderId: Long): Long {
        jdbcTemplate.update(
            "INSERT INTO payment_methods (name, type, holder_id, group_id) VALUES ('Cartao Teste', 'CREDIT', $holderId, 1)"
        )
        return jdbcTemplate.queryForObject("SELECT MAX(id) FROM payment_methods", Long::class.java)!!
    }

    private fun paymentMethodId(): Long = insertPaymentMethod(insertHolder())

    @Test
    fun `POST manual with explicit offset persists and returns the correct UTC instant without warning`() {
        val paymentMethodId = paymentMethodId()

        mockMvc.perform(
            post("/payments/notifications/manual")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{
                        "merchantName": "Apple Store",
                        "amount": 300.00,
                        "purchasedAt": "2026-01-15T10:00:00-03:00",
                        "paymentMethodId": $paymentMethodId
                    }"""
                )
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.purchasedAt").value("2026-01-15T13:00:00Z"))

        assertEquals(0, appender.list.size)
    }

    @Test
    fun `POST manual without offset falls back to America Sao_Paulo and logs a WARN`() {
        val paymentMethodId = paymentMethodId()

        mockMvc.perform(
            post("/payments/notifications/manual")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{
                        "merchantName": "Apple Store",
                        "amount": 300.00,
                        "purchasedAt": "2026-01-15T10:00:00",
                        "paymentMethodId": $paymentMethodId
                    }"""
                )
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.purchasedAt").value("2026-01-15T13:00:00Z"))

        assertEquals(1, appender.list.size)
        assertEquals(Level.WARN, appender.list[0].level)
    }

    @Test
    fun `PATCH purchased-at with explicit offset persists and returns the correct UTC instant without warning`() {
        val paymentMethodId = paymentMethodId()
        jdbcTemplate.update(
            "INSERT INTO payment_notifications " +
                "(purchased_at, amount, merchant_name, number_of_installments, origin, origin_type, payment_method_id, group_id) " +
                "VALUES ('2024-06-15 10:00:00', 150.00, 'Loja Test', 1, 'MANUAL', 'MANUAL', $paymentMethodId, 1)"
        )
        val notificationId = jdbcTemplate.queryForObject("SELECT MAX(id) FROM payment_notifications", Long::class.java)!!

        mockMvc.perform(
            patch("/payments/notifications/$notificationId/purchased-at")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"purchasedAt": "2026-01-15T10:00:00-03:00"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.purchasedAt").value("2026-01-15T13:00:00Z"))

        assertEquals(0, appender.list.size)
    }

    @Test
    fun `PATCH purchased-at without offset falls back to America Sao_Paulo and logs a WARN`() {
        val paymentMethodId = paymentMethodId()
        jdbcTemplate.update(
            "INSERT INTO payment_notifications " +
                "(purchased_at, amount, merchant_name, number_of_installments, origin, origin_type, payment_method_id, group_id) " +
                "VALUES ('2024-06-15 10:00:00', 150.00, 'Loja Test', 1, 'MANUAL', 'MANUAL', $paymentMethodId, 1)"
        )
        val notificationId = jdbcTemplate.queryForObject("SELECT MAX(id) FROM payment_notifications", Long::class.java)!!

        mockMvc.perform(
            patch("/payments/notifications/$notificationId/purchased-at")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"purchasedAt": "2026-01-15T10:00:00"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.purchasedAt").value("2026-01-15T13:00:00Z"))

        assertEquals(1, appender.list.size)
        assertEquals(Level.WARN, appender.list[0].level)
    }
}
