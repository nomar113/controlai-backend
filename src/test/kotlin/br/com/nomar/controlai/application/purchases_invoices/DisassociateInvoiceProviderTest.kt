package br.com.nomar.controlai.application.purchases_invoices

import br.com.nomar.controlai.application.payments_notification.entrypoint.database.model.PaymentNotification
import br.com.nomar.controlai.application.payments_notification.entrypoint.database.repository.PaymentNotificationRepository
import br.com.nomar.controlai.application.purchases_invoices.application.DisassociateInvoiceProvider
import br.com.nomar.controlai.application.purchases_invoices.entrypoint.database.model.PurchaseInvoiceModel
import br.com.nomar.controlai.application.purchases_invoices.entrypoint.database.repository.PurchaseInvoiceRepository
import br.com.nomar.controlai.config.TestSecurityContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDateTime
import kotlin.test.assertNull
import kotlin.test.assertTrue

@SpringBootTest
class DisassociateInvoiceProviderTest {

    @Autowired
    private lateinit var provider: DisassociateInvoiceProvider

    @Autowired
    private lateinit var invoiceRepository: PurchaseInvoiceRepository

    @Autowired
    private lateinit var notificationRepository: PaymentNotificationRepository

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    private val createdInvoiceIds = mutableListOf<Long>()
    private val createdNotificationIds = mutableListOf<Long>()

    @BeforeEach
    fun authenticate() = TestSecurityContext.authenticateAsGroup()

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
        TestSecurityContext.clear()
    }

    private fun createInvoice(cancelledAt: LocalDateTime? = null): PurchaseInvoiceModel {
        val invoice = invoiceRepository.save(
            PurchaseInvoiceModel(
                groupId = 1L,
                date = Instant.now(),
                merchantName = "Mercado Teste Disassociate",
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

    private fun createNotification(purchaseInvoiceId: Long? = null): PaymentNotification {
        val notification = notificationRepository.save(
            PaymentNotification(
                purchasedAt = Instant.now(),
                amount = BigDecimal("50.00"),
                merchantName = "Mercado Teste",
                origin = "MANUAL",
                originType = "MANUAL",
                purchaseInvoiceId = purchaseInvoiceId,
            )
        )
        createdNotificationIds.add(notification.id)
        return notification
    }

    @Test
    fun `should disassociate successfully`() {
        val invoice = createInvoice()
        val notification = createNotification(purchaseInvoiceId = invoice.id)

        val result = provider.execute(invoice.id!!)

        assertTrue(result.isSuccess)
        val updated = notificationRepository.findById(notification.id).get()
        assertNull(updated.purchaseInvoiceId)
    }

    @Test
    fun `should succeed when invoice has no association`() {
        val invoice = createInvoice()

        val result = provider.execute(invoice.id!!)

        assertTrue(result.isSuccess)
    }

    @Test
    fun `should fail when invoice not found`() {
        val result = provider.execute(999999L)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is NoSuchElementException)
    }

    @Test
    fun `should fail when invoice is cancelled`() {
        val invoice = createInvoice(cancelledAt = LocalDateTime.now())

        val result = provider.execute(invoice.id!!)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is NoSuchElementException)
    }
}
