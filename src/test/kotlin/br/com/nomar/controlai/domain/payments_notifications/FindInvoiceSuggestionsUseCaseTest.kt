package br.com.nomar.controlai.domain.payments_notifications

import br.com.nomar.controlai.application.payments_notification.entrypoint.database.model.PaymentNotification
import br.com.nomar.controlai.application.purchases_invoices.entrypoint.database.model.PurchaseInvoiceModel
import br.com.nomar.controlai.application.purchases_invoices.entrypoint.database.repository.PurchaseInvoiceRepository
import br.com.nomar.controlai.domain.payments_notifications.gateway.FindInvoiceSuggestionsGateway
import br.com.nomar.controlai.domain.payments_notifications.usecase.FindInvoiceSuggestionsUseCase
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.Optional
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FindInvoiceSuggestionsUseCaseTest {

    private val purchaseInvoiceRepository: PurchaseInvoiceRepository = mock()
    private val findSuggestionsGateway: FindInvoiceSuggestionsGateway = mock()
    private val useCase = FindInvoiceSuggestionsUseCase(purchaseInvoiceRepository, findSuggestionsGateway)

    private val invoiceDate = OffsetDateTime.of(2025, 5, 20, 14, 30, 0, 0, ZoneOffset.UTC)
    private val invoiceTotal = BigDecimal("150.00")

    private fun createInvoice(id: Long = 1L) = PurchaseInvoiceModel(
        id = id,
        date = invoiceDate,
        merchantName = "Supermercado X",
        merchantAddress = null,
        cnpj = null,
        totalItems = null,
        invoiceUrl = null,
        accessKey = null,
        subtotal = null,
        total = invoiceTotal,
        taxes = null,
        discount = null,
    )

    private fun createNotification(id: Long, minutesDelta: Long = 0) = PaymentNotification(
        id = id,
        cardLastDigits = "1234",
        purchasedAt = invoiceDate.toLocalDateTime().plusMinutes(minutesDelta),
        amount = invoiceTotal,
        merchantName = "Supermercado X",
        numberOfInstallments = 1,
    )

    @Test
    fun `should return failure with NoSuchElementException when invoice not found`() {
        `when`(purchaseInvoiceRepository.findById(99L)).thenReturn(Optional.empty())

        val result = useCase.execute(99L)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is NoSuchElementException)
        assertEquals("Invoice not found: 99", result.exceptionOrNull()?.message)
    }

    @Test
    fun `should return list of suggestions when gateway returns results`() {
        val invoice = createInvoice()
        val notifications = listOf(
            createNotification(10L, minutesDelta = 3),
            createNotification(20L, minutesDelta = 15),
            createNotification(30L, minutesDelta = 45),
        )
        val invoiceLocalDate = invoiceDate.toLocalDateTime()

        `when`(purchaseInvoiceRepository.findById(1L)).thenReturn(Optional.of(invoice))
        `when`(
            findSuggestionsGateway.execute(
                amount = invoiceTotal,
                startDate = invoiceLocalDate.minusHours(1),
                endDate = invoiceLocalDate.plusHours(1),
                invoiceDate = invoiceLocalDate,
            )
        ).thenReturn(Result.success(notifications))

        val result = useCase.execute(1L)

        assertTrue(result.isSuccess)
        val suggestionsResult = result.getOrNull()!!
        assertEquals(3, suggestionsResult.notifications.size)
        assertEquals(10L, suggestionsResult.notifications[0].id)
        assertEquals(20L, suggestionsResult.notifications[1].id)
        assertEquals(30L, suggestionsResult.notifications[2].id)
        assertEquals(invoiceDate, suggestionsResult.invoiceDate)

        verify(findSuggestionsGateway).execute(
            amount = invoiceTotal,
            startDate = invoiceLocalDate.minusHours(1),
            endDate = invoiceLocalDate.plusHours(1),
            invoiceDate = invoiceLocalDate,
        )
    }

    @Test
    fun `should return empty list when gateway returns no results`() {
        val invoice = createInvoice()
        val invoiceLocalDate = invoiceDate.toLocalDateTime()

        `when`(purchaseInvoiceRepository.findById(1L)).thenReturn(Optional.of(invoice))
        `when`(
            findSuggestionsGateway.execute(
                amount = invoiceTotal,
                startDate = invoiceLocalDate.minusHours(1),
                endDate = invoiceLocalDate.plusHours(1),
                invoiceDate = invoiceLocalDate,
            )
        ).thenReturn(Result.success(emptyList()))

        val result = useCase.execute(1L)

        assertTrue(result.isSuccess)
        val suggestionsResult = result.getOrNull()!!
        assertTrue(suggestionsResult.notifications.isEmpty())
        assertEquals(invoiceDate, suggestionsResult.invoiceDate)
    }
}
