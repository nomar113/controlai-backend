package br.com.nomar.controlai.application.payments_notification

import br.com.nomar.controlai.application.payments_notification.application.FindNotificationInvoiceSuggestionsProvider
import br.com.nomar.controlai.application.payments_notification.entrypoint.database.model.PaymentNotification
import br.com.nomar.controlai.application.payments_notification.entrypoint.database.repository.PaymentNotificationRepository
import br.com.nomar.controlai.application.purchases_invoices.entrypoint.database.model.PurchaseInvoiceModel
import br.com.nomar.controlai.application.purchases_invoices.entrypoint.database.repository.PurchaseInvoiceRepository
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.Optional
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FindNotificationInvoiceSuggestionsProviderTest {

    private val paymentNotificationRepository: PaymentNotificationRepository = mock()
    private val purchaseInvoiceRepository: PurchaseInvoiceRepository = mock()
    private val provider = FindNotificationInvoiceSuggestionsProvider(
        paymentNotificationRepository,
        purchaseInvoiceRepository,
    )

    private val amount = BigDecimal("150.00")
    private val purchasedAt = LocalDateTime.of(2024, 6, 15, 14, 0, 0)

    private fun createNotification(id: Long = 1L) = PaymentNotification(
        id = id,
        purchasedAt = purchasedAt,
        amount = amount,
        merchantName = "Loja Teste",
        numberOfInstallments = 1,
        origin = "NUBANK",
        originType = "HTTP_REQUEST",
    )

    private fun createInvoice(id: Long, dateDeltaMinutes: Long = 0) = PurchaseInvoiceModel(
        id = id,
        date = OffsetDateTime.of(purchasedAt.plusMinutes(dateDeltaMinutes), ZoneOffset.UTC),
        merchantName = "Loja Teste",
        merchantAddress = null,
        cnpj = "12.345.678/0001-90",
        totalItems = 3,
        invoiceUrl = null,
        accessKey = null,
        subtotal = null,
        total = amount,
        taxes = null,
        discount = null,
    )

    @Test
    fun `should return failure when notification not found`() {
        `when`(paymentNotificationRepository.findById(99L)).thenReturn(Optional.empty())

        val result = provider.execute(99L)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is NoSuchElementException)
        assertEquals("PaymentNotification not found: 99", result.exceptionOrNull()?.message)
    }

    @Test
    fun `should return empty list when no invoice candidates exist`() {
        val notification = createNotification()
        `when`(paymentNotificationRepository.findById(1L)).thenReturn(Optional.of(notification))
        `when`(purchaseInvoiceRepository.findByTotalAndNotAssociated(amount, purchasedAt)).thenReturn(emptyList())

        val result = provider.execute(1L)

        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().isEmpty())
    }

    @Test
    fun `should return mapped suggestions ordered by date proximity`() {
        val notification = createNotification()
        val invoice1 = createInvoice(id = 10L, dateDeltaMinutes = 5)
        val invoice2 = createInvoice(id = 20L, dateDeltaMinutes = 30)
        val invoice3 = createInvoice(id = 30L, dateDeltaMinutes = 120)

        `when`(paymentNotificationRepository.findById(1L)).thenReturn(Optional.of(notification))
        `when`(purchaseInvoiceRepository.findByTotalAndNotAssociated(amount, purchasedAt))
            .thenReturn(listOf(invoice1, invoice2, invoice3))

        val result = provider.execute(1L)

        assertTrue(result.isSuccess)
        val suggestions = result.getOrThrow()
        assertEquals(3, suggestions.size)
        assertEquals(10L, suggestions[0].id)
        assertEquals(20L, suggestions[1].id)
        assertEquals(30L, suggestions[2].id)
        assertEquals(amount, suggestions[0].total)
        assertEquals("12.345.678/0001-90", suggestions[0].cnpj)
        assertEquals("Loja Teste", suggestions[0].merchantName)
        assertEquals(3, suggestions[0].totalItems)
    }

    @Test
    fun `should return failure when repository throws exception`() {
        val notification = createNotification()
        `when`(paymentNotificationRepository.findById(1L)).thenReturn(Optional.of(notification))
        `when`(purchaseInvoiceRepository.findByTotalAndNotAssociated(amount, purchasedAt))
            .thenThrow(RuntimeException("Database error"))

        val result = provider.execute(1L)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is RuntimeException)
    }
}
