package br.com.nomar.controlai.application.payments_notification.entrypoint.rest.request

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.Instant

/**
 * Cobre o requisito 6 do PRD: requests com `purchasedAt` devem ter o timezone de origem
 * tratado de forma explicita e nao ambigua. Strings ISO-8601 com offset sao aceitas
 * diretamente; strings sem offset (clientes desatualizados) sao aceitas como fallback,
 * interpretadas em America/Sao_Paulo, e emitem um log WARN (Tarefa 4.3).
 */
@SpringBootTest
class PurchasedAtDeserializerTest {

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    private lateinit var appender: ListAppender<ILoggingEvent>

    @BeforeEach
    fun attachAppender() {
        appender = ListAppender()
        appender.start()
        (LoggerFactory.getLogger(PurchasedAtDeserializer::class.java) as Logger).addAppender(appender)
    }

    @AfterEach
    fun detachAppender() {
        (LoggerFactory.getLogger(PurchasedAtDeserializer::class.java) as Logger).detachAppender(appender)
    }

    @Test
    fun `UpdatePurchasedAtRequest with explicit negative offset deserializes to the correct UTC instant without warning`() {
        val request = objectMapper.readValue(
            """{"purchasedAt": "2026-08-23T10:00:00-03:00"}""",
            UpdatePurchasedAtRequest::class.java,
        )

        assertEquals(Instant.parse("2026-08-23T13:00:00Z"), request.purchasedAt)
        assertTrue(appender.list.isEmpty())
    }

    @Test
    fun `UpdatePurchasedAtRequest with Z suffix deserializes directly as UTC without warning`() {
        val request = objectMapper.readValue(
            """{"purchasedAt": "2026-08-23T10:00:00Z"}""",
            UpdatePurchasedAtRequest::class.java,
        )

        assertEquals(Instant.parse("2026-08-23T10:00:00Z"), request.purchasedAt)
        assertTrue(appender.list.isEmpty())
    }

    @Test
    fun `UpdatePurchasedAtRequest without offset falls back to America Sao_Paulo and logs a WARN`() {
        val request = objectMapper.readValue(
            """{"purchasedAt": "2026-08-23T10:00:00"}""",
            UpdatePurchasedAtRequest::class.java,
        )

        assertEquals(Instant.parse("2026-08-23T13:00:00Z"), request.purchasedAt)
        assertEquals(1, appender.list.size)
        assertEquals(Level.WARN, appender.list[0].level)
    }

    @Test
    fun `ManualPaymentNotificationRequest without offset falls back to America Sao_Paulo and logs a WARN`() {
        val request = objectMapper.readValue(
            """
            {
                "merchantName": "Loja",
                "amount": 10.00,
                "purchasedAt": "2026-08-23T10:00:00",
                "paymentMethodId": 1
            }
            """.trimIndent(),
            ManualPaymentNotificationRequest::class.java,
        )

        assertEquals(Instant.parse("2026-08-23T13:00:00Z"), request.purchasedAt)
        assertEquals(1, appender.list.size)
        assertEquals(Level.WARN, appender.list[0].level)
    }
}
