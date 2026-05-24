package br.com.nomar.controlai.application.purchases_invoices

import br.com.nomar.controlai.application.payments_notification.entrypoint.database.model.PaymentNotification
import br.com.nomar.controlai.application.payments_notification.entrypoint.database.repository.PaymentNotificationRepository
import br.com.nomar.controlai.application.purchases_invoices.application.AssociateInvoiceProvider
import br.com.nomar.controlai.application.purchases_invoices.entrypoint.database.model.PurchaseInvoiceModel
import br.com.nomar.controlai.application.purchases_invoices.entrypoint.database.repository.PurchaseInvoiceRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.OffsetDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@SpringBootTest
class AssociateInvoiceProviderTest {

    @Autowired
    private lateinit var provider: AssociateInvoiceProvider

    @Autowired
    private lateinit var invoiceRepository: PurchaseInvoiceRepository

    @Autowired
    private lateinit var notificationRepository: PaymentNotificationRepository

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    private val createdInvoiceIds = mutableListOf<Long>()
    private val createdNotificationIds = mutableListOf<Long>()

    @AfterEach
    fun cleanup() {
        createdNotificationIds.forEach { id ->
            jdbcTemplate.update("DELETE FROM payment_notifications WHERE id = ?", id)
        }
        createdInvoiceIds.forEach { id ->
            jdbcTemplate.update("DELETE FROM purchase_invoices WHERE id = ?", id)
        }
        createdNotificationIds.clear()
        createdInvoiceIds.clear()
    }

    private fun createInvoice(cancelledAt: LocalDateTime? = null): PurchaseInvoiceModel {
        val invoice = invoiceRepository.save(
            PurchaseInvoiceModel(
                date = OffsetDateTime.now(),
                merchantName = "Mercado Teste Associate",
                merchantAddress = "Rua A",
                cnpj = "12345678000199",
                totalItems = 3,
                invoiceUrl = null,
                accessKey = null,
                subtotal = BigDecimal("50.00"),
                total = BigDecimal("50.00"),
                taxes = BigDecimal.ZERO,
                discount = BigDecimal.ZERO,
                cancelledAt = cancelledAt,
            )
        )
        createdInvoiceIds.add(invoice.id!!)
        return invoice
    }

    private fun createNotification(
        purchaseInvoiceId: Long? = null,
        cancelledAt: LocalDateTime? = null,
    ): PaymentNotification {
        val notification = notificationRepository.save(
            PaymentNotification(
                purchasedAt = LocalDateTime.now(),
                amount = BigDecimal("50.00"),
                merchantName = "Mercado Teste",
                origin = "MANUAL",
                originType = "MANUAL",
                purchaseInvoiceId = purchaseInvoiceId,
                cancelledAt = cancelledAt,
            )
        )
        createdNotificationIds.add(notification.id)
        return notification
    }

    @Test
    fun `should associate invoice successfully`() {
        val invoice = createInvoice()
        val notification = createNotification()

        val result = provider.execute(invoice.id!!, notification.id)

        assertTrue(result.isSuccess)
        val response = result.getOrThrow()
        assertEquals(invoice.id, response.invoiceId)
        assertEquals(notification.id, response.paymentNotificationId)
        assertNotNull(response.associatedAt)

        val updated = notificationRepository.findById(notification.id).get()
        assertEquals(invoice.id, updated.purchaseInvoiceId)
    }

    @Test
    fun `should fail when invoice not found`() {
        val notification = createNotification()

        val result = provider.execute(999999L, notification.id)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is NoSuchElementException)
    }

    @Test
    fun `should fail when notification not found`() {
        val invoice = createInvoice()

        val result = provider.execute(invoice.id!!, 999999L)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is NoSuchElementException)
    }

    @Test
    fun `should fail when notification already associated to another invoice`() {
        val invoice1 = createInvoice()
        val invoice2 = createInvoice()
        val notification = createNotification(purchaseInvoiceId = invoice1.id)

        val result = provider.execute(invoice2.id!!, notification.id)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
    }

    @Test
    fun `should re-associate when invoice already has a notification`() {
        val invoice = createInvoice()
        val notification1 = createNotification(purchaseInvoiceId = invoice.id)
        val notification2 = createNotification()

        val result = provider.execute(invoice.id!!, notification2.id)

        assertTrue(result.isSuccess)

        val updatedNotification1 = notificationRepository.findById(notification1.id).get()
        assertNull(updatedNotification1.purchaseInvoiceId)

        val updatedNotification2 = notificationRepository.findById(notification2.id).get()
        assertEquals(invoice.id, updatedNotification2.purchaseInvoiceId)
    }

    @Test
    fun `should succeed when associating same notification to same invoice`() {
        val invoice = createInvoice()
        val notification = createNotification(purchaseInvoiceId = invoice.id)

        val result = provider.execute(invoice.id!!, notification.id)

        assertTrue(result.isSuccess)
        val updated = notificationRepository.findById(notification.id).get()
        assertEquals(invoice.id, updated.purchaseInvoiceId)
    }

    @Test
    fun `should fail when invoice is cancelled`() {
        val invoice = createInvoice(cancelledAt = LocalDateTime.now())
        val notification = createNotification()

        val result = provider.execute(invoice.id!!, notification.id)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is NoSuchElementException)
    }

    @Test
    fun `should fail when notification is cancelled`() {
        val invoice = createInvoice()
        val notification = createNotification(cancelledAt = LocalDateTime.now())

        val result = provider.execute(invoice.id!!, notification.id)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is NoSuchElementException)
    }
}
