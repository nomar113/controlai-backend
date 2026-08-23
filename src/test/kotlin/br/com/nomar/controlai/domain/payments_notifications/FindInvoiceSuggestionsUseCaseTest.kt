package br.com.nomar.controlai.domain.payments_notifications

import br.com.nomar.controlai.application.payments_notification.entrypoint.database.model.PaymentNotification
import br.com.nomar.controlai.application.purchases_invoices.entrypoint.database.model.PurchaseInvoiceModel
import br.com.nomar.controlai.application.purchases_invoices.entrypoint.database.repository.PurchaseInvoiceRepository
import br.com.nomar.controlai.domain.auth.RequestContext
import br.com.nomar.controlai.domain.payments_notifications.gateway.FindInvoiceSuggestionsGateway
import br.com.nomar.controlai.domain.payments_notifications.usecase.FindInvoiceSuggestionsUseCase
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.math.BigDecimal
import java.time.Duration
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FindInvoiceSuggestionsUseCaseTest {

    private val purchaseInvoiceRepository: PurchaseInvoiceRepository = mock()
    private val findSuggestionsGateway: FindInvoiceSuggestionsGateway = mock()
    private val requestContext: RequestContext = mock<RequestContext>().also { `when`(it.groupId).thenReturn(1L) }
    private val useCase = FindInvoiceSuggestionsUseCase(purchaseInvoiceRepository, findSuggestionsGateway, requestContext)

    private val invoiceDate = OffsetDateTime.of(2025, 5, 20, 14, 30, 0, 0, ZoneOffset.UTC).toInstant()
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
        purchasedAt = invoiceDate.plus(Duration.ofMinutes(minutesDelta)),
        amount = invoiceTotal,
        merchantName = "Supermercado X",
        numberOfInstallments = 1,
    )

    @Test
    fun `should return failure with NoSuchElementException when invoice not found`() {
        `when`(purchaseInvoiceRepository.findByIdAndGroupId(99L, 1L)).thenReturn(null)

        val result = useCase.execute(99L)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is NoSuchElementException)
        assertEquals("Invoice not found: 99", result.exceptionOrNull()?.message)
    }

    @Test
    fun `should query gateway only by invoice amount`() {
        val invoice = createInvoice()
        val notifications = listOf(
            createNotification(10L, minutesDelta = 3),
            createNotification(20L, minutesDelta = 15),
            createNotification(30L, minutesDelta = 45),
        )

        `when`(purchaseInvoiceRepository.findByIdAndGroupId(1L, 1L)).thenReturn(invoice)
        `when`(findSuggestionsGateway.execute(invoiceTotal)).thenReturn(Result.success(notifications))

        val result = useCase.execute(1L)

        assertTrue(result.isSuccess)
        val suggestionsResult = result.getOrNull()!!
        assertEquals(3, suggestionsResult.notifications.size)
        assertEquals(10L, suggestionsResult.notifications[0].id)
        assertEquals(20L, suggestionsResult.notifications[1].id)
        assertEquals(30L, suggestionsResult.notifications[2].id)
        assertEquals(invoiceDate, suggestionsResult.invoiceDate)

        verify(findSuggestionsGateway).execute(invoiceTotal)
    }

    @Test
    fun `should return empty list when gateway returns no results`() {
        val invoice = createInvoice()

        `when`(purchaseInvoiceRepository.findByIdAndGroupId(1L, 1L)).thenReturn(invoice)
        `when`(findSuggestionsGateway.execute(invoiceTotal)).thenReturn(Result.success(emptyList()))

        val result = useCase.execute(1L)

        assertTrue(result.isSuccess)
        val suggestionsResult = result.getOrNull()!!
        assertTrue(suggestionsResult.notifications.isEmpty())
        assertEquals(invoiceDate, suggestionsResult.invoiceDate)
    }
}
