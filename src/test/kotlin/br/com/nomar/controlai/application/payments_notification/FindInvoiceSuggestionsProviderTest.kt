package br.com.nomar.controlai.application.payments_notification

import br.com.nomar.controlai.application.payments_notification.application.FindInvoiceSuggestionsProvider
import br.com.nomar.controlai.application.payments_notification.entrypoint.database.model.PaymentNotification
import br.com.nomar.controlai.application.payments_notification.entrypoint.database.repository.PaymentNotificationRepository
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.math.BigDecimal
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FindInvoiceSuggestionsProviderTest {

    private val paymentNotificationRepository: PaymentNotificationRepository = mock()
    private val provider = FindInvoiceSuggestionsProvider(paymentNotificationRepository)

    private val invoiceDate = LocalDateTime.of(2024, 6, 15, 14, 0, 0)
    private val startDate = invoiceDate.minusHours(1)
    private val endDate = invoiceDate.plusHours(1)
    private val amount = BigDecimal("150.00")

    private fun createNotification(id: Long, purchasedAt: LocalDateTime): PaymentNotification {
        return PaymentNotification(
            id = id,
            purchasedAt = purchasedAt,
            amount = amount,
            merchantName = "Loja Teste",
            numberOfInstallments = 1,
            origin = "NUBANK",
            originType = "HTTP_REQUEST",
        )
    }

    @Test
    fun `should return suggestions from repository`() {
        val notification1 = createNotification(1L, invoiceDate.plusMinutes(10))
        val notification2 = createNotification(2L, invoiceDate.plusMinutes(50))

        `when`(
            paymentNotificationRepository.findSuggestionsByAmountAndDateRange(
                amount = amount,
                startDate = startDate,
                endDate = endDate,
                invoiceDate = invoiceDate,
            )
        ).thenReturn(listOf(notification1, notification2))

        val result = provider.execute(amount, startDate, endDate, invoiceDate)

        assertTrue(result.isSuccess)
        val suggestions = result.getOrThrow()
        assertEquals(2, suggestions.size)
        assertEquals(1L, suggestions[0].id)
        assertEquals(2L, suggestions[1].id)
    }

    @Test
    fun `should return empty list when no matches`() {
        `when`(
            paymentNotificationRepository.findSuggestionsByAmountAndDateRange(
                amount = amount,
                startDate = startDate,
                endDate = endDate,
                invoiceDate = invoiceDate,
            )
        ).thenReturn(emptyList())

        val result = provider.execute(amount, startDate, endDate, invoiceDate)

        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().isEmpty())
    }

    @Test
    fun `should return failure when repository throws exception`() {
        `when`(
            paymentNotificationRepository.findSuggestionsByAmountAndDateRange(
                amount = amount,
                startDate = startDate,
                endDate = endDate,
                invoiceDate = invoiceDate,
            )
        ).thenThrow(RuntimeException("Database error"))

        val result = provider.execute(amount, startDate, endDate, invoiceDate)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is RuntimeException)
    }
}
