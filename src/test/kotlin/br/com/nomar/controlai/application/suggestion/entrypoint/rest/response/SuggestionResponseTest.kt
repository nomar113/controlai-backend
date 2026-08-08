package br.com.nomar.controlai.application.suggestion.entrypoint.rest.response

import br.com.nomar.controlai.application.categories.entrypoint.database.model.CategoryModel
import br.com.nomar.controlai.application.payments_notification.entrypoint.database.model.PaymentNotification
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.test.assertEquals

class SuggestionResponseTest {

    private val invoiceDate = OffsetDateTime.of(2025, 5, 20, 14, 30, 0, 0, ZoneOffset.UTC)

    private fun createNotification(
        id: Long = 1L,
        purchasedAt: LocalDateTime = invoiceDate.toLocalDateTime(),
    ) = PaymentNotification(
        id = id,
        cardLastDigits = "1234",
        purchasedAt = purchasedAt,
        amount = BigDecimal("150.00"),
        merchantName = "Supermercado X",
        numberOfInstallments = 2,
        category = CategoryModel(id = 5L, groupId = 1, name = "Alimentacao"),
        origin = "WHATSAPP",
        originType = "PUSH_NOTIFICATION",
    )

    @Test
    fun `should calculate timeDeltaMinutes as 0 when times are equal`() {
        val notification = createNotification(purchasedAt = invoiceDate.toLocalDateTime())

        val response = SuggestionResponse.from(notification, invoiceDate)

        assertEquals(0L, response.timeDeltaMinutes)
    }

    @Test
    fun `should calculate timeDeltaMinutes as 30 when notification is 30 minutes after invoice`() {
        val notification = createNotification(
            purchasedAt = invoiceDate.toLocalDateTime().plusMinutes(30),
        )

        val response = SuggestionResponse.from(notification, invoiceDate)

        assertEquals(30L, response.timeDeltaMinutes)
    }

    @Test
    fun `should calculate timeDeltaMinutes as 30 when notification is 30 minutes before invoice`() {
        val notification = createNotification(
            purchasedAt = invoiceDate.toLocalDateTime().minusMinutes(30),
        )

        val response = SuggestionResponse.from(notification, invoiceDate)

        assertEquals(30L, response.timeDeltaMinutes)
    }

    @Test
    fun `should calculate timeDeltaMinutes as 59 when notification is 59 minutes away`() {
        val notification = createNotification(
            purchasedAt = invoiceDate.toLocalDateTime().plusMinutes(59),
        )

        val response = SuggestionResponse.from(notification, invoiceDate)

        assertEquals(59L, response.timeDeltaMinutes)
    }

    @Test
    fun `should map all fields correctly from PaymentNotification`() {
        val notification = createNotification()

        val response = SuggestionResponse.from(notification, invoiceDate)

        assertEquals(1L, response.id)
        assertEquals("1234", response.cardLastDigits)
        assertEquals(invoiceDate.toLocalDateTime(), response.purchasedAt)
        assertEquals(BigDecimal("150.00"), response.amount)
        assertEquals("Supermercado X", response.merchantName)
        assertEquals(2, response.numberOfInstallments)
        assertEquals("Alimentacao", response.category)
        assertEquals(5L, response.categoryId)
        assertEquals("WHATSAPP", response.origin)
        assertEquals("PUSH_NOTIFICATION", response.originType)
    }

    @Test
    fun `should handle null optional fields`() {
        val notification = PaymentNotification(
            id = 2L,
            cardLastDigits = null,
            purchasedAt = invoiceDate.toLocalDateTime().plusMinutes(10),
            amount = BigDecimal("50.00"),
            merchantName = "Loja Y",
            numberOfInstallments = 1,
            category = null,
        )

        val response = SuggestionResponse.from(notification, invoiceDate)

        assertEquals(null, response.cardLastDigits)
        assertEquals(null, response.category)
        assertEquals(null, response.categoryId)
        assertEquals("HTTP_REQUEST", response.origin)
        assertEquals("HTTP_REQUEST", response.originType)
        assertEquals(10L, response.timeDeltaMinutes)
    }
}
