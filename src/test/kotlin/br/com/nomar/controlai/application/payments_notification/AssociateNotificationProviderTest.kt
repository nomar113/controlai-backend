package br.com.nomar.controlai.application.payments_notification

import br.com.nomar.controlai.application.payments_notification.application.AssociateNotificationProvider
import br.com.nomar.controlai.application.payments_notification.entrypoint.database.model.PaymentNotification
import br.com.nomar.controlai.application.payments_notification.entrypoint.database.repository.PaymentNotificationRepository
import br.com.nomar.controlai.application.purchases_invoices.entrypoint.database.model.PurchaseInvoiceModel
import br.com.nomar.controlai.application.purchases_invoices.entrypoint.database.repository.PurchaseInvoiceRepository
import br.com.nomar.controlai.domain.auth.RequestContext
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AssociateNotificationProviderTest {

    private val paymentNotificationRepository: PaymentNotificationRepository = mock()
    private val purchaseInvoiceRepository: PurchaseInvoiceRepository = mock()
    private val requestContext: RequestContext = mock<RequestContext>().also { `when`(it.groupId).thenReturn(1L) }
    private val provider = AssociateNotificationProvider(
        paymentNotificationRepository,
        purchaseInvoiceRepository,
        requestContext,
    )

    private val notificationId = 1L
    private val purchaseInvoiceId = 10L

    private fun createNotification(
        id: Long = notificationId,
        purchaseInvoiceId: Long? = null,
        cancelledAt: LocalDateTime? = null,
    ) = PaymentNotification(
        id = id,
        purchasedAt = LocalDateTime.of(2024, 6, 15, 14, 0, 0).toInstant(ZoneOffset.UTC),
        amount = BigDecimal("150.00"),
        merchantName = "Loja Teste",
        numberOfInstallments = 1,
        origin = "NUBANK",
        originType = "HTTP_REQUEST",
        purchaseInvoiceId = purchaseInvoiceId,
        cancelledAt = cancelledAt,
    )

    private fun createInvoice(
        id: Long = purchaseInvoiceId,
        cancelledAt: LocalDateTime? = null,
    ) = PurchaseInvoiceModel(
        id = id,
        date = OffsetDateTime.of(2024, 6, 15, 14, 0, 0, 0, ZoneOffset.UTC).toInstant(),
        merchantName = "Mercado Teste",
        merchantAddress = null,
        cnpj = "12.345.678/0001-90",
        totalItems = 5,
        invoiceUrl = null,
        accessKey = null,
        subtotal = null,
        total = BigDecimal("150.00"),
        taxes = null,
        discount = null,
        cancelledAt = cancelledAt,
    )

    @Test
    fun `should associate notification successfully`() {
        val notification = createNotification()
        val invoice = createInvoice()
        val savedNotification = createNotification(purchaseInvoiceId = purchaseInvoiceId)

        `when`(paymentNotificationRepository.findByIdAndGroupId(notificationId, 1L)).thenReturn(notification)
        `when`(purchaseInvoiceRepository.findByIdAndGroupId(purchaseInvoiceId, 1L)).thenReturn(invoice)
        `when`(paymentNotificationRepository.saveAndFlush(notification)).thenReturn(savedNotification)

        val result = provider.execute(notificationId, purchaseInvoiceId)

        assertTrue(result.isSuccess)
        val response = result.getOrThrow()
        assertEquals(notificationId, response.id)
        assertEquals(purchaseInvoiceId, response.purchaseInvoiceId)
        val associatedInvoice = response.associatedInvoice
        assertNotNull(associatedInvoice)
        assertEquals(purchaseInvoiceId, associatedInvoice.id)
        assertEquals("Mercado Teste", associatedInvoice.merchantName)
        assertEquals("12.345.678/0001-90", associatedInvoice.cnpj)
        assertEquals(5, associatedInvoice.totalItems)
        assertEquals(BigDecimal("150.00"), associatedInvoice.total)
        verify(paymentNotificationRepository).saveAndFlush(notification)
    }

    @Test
    fun `should return failure when notification not found`() {
        `when`(paymentNotificationRepository.findByIdAndGroupId(notificationId, 1L)).thenReturn(null)

        val result = provider.execute(notificationId, purchaseInvoiceId)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is NoSuchElementException)
        assertEquals("PaymentNotification not found: $notificationId", result.exceptionOrNull()?.message)
    }

    @Test
    fun `should return failure when notification is cancelled`() {
        val cancelledNotification = createNotification(cancelledAt = LocalDateTime.now())
        `when`(paymentNotificationRepository.findByIdAndGroupId(notificationId, 1L)).thenReturn(cancelledNotification)

        val result = provider.execute(notificationId, purchaseInvoiceId)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is NoSuchElementException)
        assertEquals("PaymentNotification is cancelled: $notificationId", result.exceptionOrNull()?.message)
    }

    @Test
    fun `should return failure when invoice not found`() {
        val notification = createNotification()
        `when`(paymentNotificationRepository.findByIdAndGroupId(notificationId, 1L)).thenReturn(notification)
        `when`(purchaseInvoiceRepository.findByIdAndGroupId(purchaseInvoiceId, 1L)).thenReturn(null)

        val result = provider.execute(notificationId, purchaseInvoiceId)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is NoSuchElementException)
        assertEquals("PurchaseInvoice not found: $purchaseInvoiceId", result.exceptionOrNull()?.message)
    }

    @Test
    fun `should return failure when invoice is cancelled`() {
        val notification = createNotification()
        val cancelledInvoice = createInvoice(cancelledAt = LocalDateTime.now())
        `when`(paymentNotificationRepository.findByIdAndGroupId(notificationId, 1L)).thenReturn(notification)
        `when`(purchaseInvoiceRepository.findByIdAndGroupId(purchaseInvoiceId, 1L)).thenReturn(cancelledInvoice)

        val result = provider.execute(notificationId, purchaseInvoiceId)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is NoSuchElementException)
        assertEquals("PurchaseInvoice is cancelled: $purchaseInvoiceId", result.exceptionOrNull()?.message)
    }

    @Test
    fun `should return 409 when notification already has an associated invoice`() {
        val alreadyAssociatedNotification = createNotification(purchaseInvoiceId = 99L)
        `when`(paymentNotificationRepository.findByIdAndGroupId(notificationId, 1L)).thenReturn(alreadyAssociatedNotification)
        `when`(purchaseInvoiceRepository.findByIdAndGroupId(purchaseInvoiceId, 1L)).thenReturn(createInvoice())

        val result = provider.execute(notificationId, purchaseInvoiceId)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
        assertEquals(
            "PaymentNotification $notificationId is already associated with invoice 99",
            result.exceptionOrNull()?.message,
        )
    }

    @Test
    fun `associatedInvoice in response contains all invoice fields`() {
        val notification = createNotification()
        val invoice = createInvoice()
        val savedNotification = createNotification(purchaseInvoiceId = purchaseInvoiceId)

        `when`(paymentNotificationRepository.findByIdAndGroupId(notificationId, 1L)).thenReturn(notification)
        `when`(purchaseInvoiceRepository.findByIdAndGroupId(purchaseInvoiceId, 1L)).thenReturn(invoice)
        `when`(paymentNotificationRepository.saveAndFlush(notification)).thenReturn(savedNotification)

        val result = provider.execute(notificationId, purchaseInvoiceId)

        assertTrue(result.isSuccess)
        val associatedInvoice = result.getOrThrow().associatedInvoice
        assertNotNull(associatedInvoice)
        assertEquals(purchaseInvoiceId, associatedInvoice.id)
        assertEquals("Mercado Teste", associatedInvoice.merchantName)
        assertEquals("12.345.678/0001-90", associatedInvoice.cnpj)
        assertEquals(5, associatedInvoice.totalItems)
        assertEquals(BigDecimal("150.00"), associatedInvoice.total)
        assertNotNull(associatedInvoice.date)
    }
}
