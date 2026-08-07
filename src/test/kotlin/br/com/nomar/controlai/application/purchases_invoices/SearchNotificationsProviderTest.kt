package br.com.nomar.controlai.application.purchases_invoices

import br.com.nomar.controlai.application.payments_notification.entrypoint.database.model.PaymentNotification
import br.com.nomar.controlai.application.payments_notification.entrypoint.database.repository.PaymentNotificationRepository
import br.com.nomar.controlai.application.purchases_invoices.application.SearchNotificationsProvider
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
import java.time.LocalDateTime
import java.time.OffsetDateTime
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@SpringBootTest
class SearchNotificationsProviderTest {

    @Autowired
    private lateinit var provider: SearchNotificationsProvider

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

    private fun createInvoice(
        cancelledAt: LocalDateTime? = null,
        deletedAt: LocalDateTime? = null,
    ): PurchaseInvoiceModel {
        val invoice = invoiceRepository.save(
            PurchaseInvoiceModel(
                groupId = 1L,
                date = OffsetDateTime.now(),
                merchantName = "Mercado Teste Search",
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
        if (deletedAt != null) {
            jdbcTemplate.update(
                "UPDATE purchase_invoices SET deleted_at = ? WHERE id = ?",
                deletedAt,
                invoice.id,
            )
        }
        return invoice
    }

    private fun createNotification(
        amount: BigDecimal = BigDecimal("50.00"),
        purchasedAt: LocalDateTime = LocalDateTime.now(),
        purchaseInvoiceId: Long? = null,
        cancelledAt: LocalDateTime? = null,
        deletedAt: LocalDateTime? = null,
        merchantName: String = "Mercado Teste",
    ): PaymentNotification {
        val notification = notificationRepository.save(
            PaymentNotification(
                purchasedAt = purchasedAt,
                amount = amount,
                merchantName = merchantName,
                origin = "MANUAL",
                originType = "MANUAL",
                purchaseInvoiceId = purchaseInvoiceId,
                cancelledAt = cancelledAt,
                deletedAt = deletedAt,
            )
        )
        createdNotificationIds.add(notification.id)
        if (deletedAt != null) {
            jdbcTemplate.update(
                "UPDATE payment_notifications SET deleted_at = ? WHERE id = ?",
                deletedAt,
                notification.id,
            )
        }
        return notification
    }

    @Test
    fun `should filter by amount when provided`() {
        val invoice = createInvoice()
        val target = createNotification(amount = BigDecimal("150.00"))
        createNotification(amount = BigDecimal("99.99"))

        val result = provider.execute(
            invoiceId = invoice.id!!,
            amount = BigDecimal("150.00"),
            startDate = null,
            endDate = null,
        )

        assertTrue(result.isSuccess)
        val suggestions = result.getOrThrow()
        assertEquals(1, suggestions.size)
        assertEquals(target.id, suggestions[0].id)
    }

    @Test
    fun `should filter by date range when start and end provided`() {
        val invoice = createInvoice()
        val baseDate = LocalDateTime.now().minusDays(2)
        val target = createNotification(purchasedAt = baseDate)
        createNotification(purchasedAt = baseDate.minusDays(30))

        val result = provider.execute(
            invoiceId = invoice.id!!,
            amount = null,
            startDate = baseDate.minusHours(1),
            endDate = baseDate.plusHours(1),
        )

        assertTrue(result.isSuccess)
        val suggestions = result.getOrThrow()
        assertEquals(1, suggestions.size)
        assertEquals(target.id, suggestions[0].id)
    }

    @Test
    fun `should filter by both amount and date range when both provided`() {
        val invoice = createInvoice()
        val baseDate = LocalDateTime.now().minusDays(1)
        val target = createNotification(amount = BigDecimal("200.00"), purchasedAt = baseDate)
        createNotification(amount = BigDecimal("200.00"), purchasedAt = baseDate.minusDays(30))
        createNotification(amount = BigDecimal("99.99"), purchasedAt = baseDate)

        val result = provider.execute(
            invoiceId = invoice.id!!,
            amount = BigDecimal("200.00"),
            startDate = baseDate.minusHours(1),
            endDate = baseDate.plusHours(1),
        )

        assertTrue(result.isSuccess)
        val suggestions = result.getOrThrow()
        assertEquals(1, suggestions.size)
        assertEquals(target.id, suggestions[0].id)
    }

    @Test
    fun `should return last 7 days when no filters provided`() {
        val invoice = createInvoice()
        val recent = createNotification(purchasedAt = LocalDateTime.now().minusDays(3))
        createNotification(purchasedAt = LocalDateTime.now().minusDays(10))

        val result = provider.execute(
            invoiceId = invoice.id!!,
            amount = null,
            startDate = null,
            endDate = null,
        )

        assertTrue(result.isSuccess)
        val suggestions = result.getOrThrow()
        val ids = suggestions.map { it.id }
        assertTrue(ids.contains(recent.id))
        assertTrue(suggestions.all { it.purchasedAt.isAfter(LocalDateTime.now().minusDays(7)) })
    }

    @Test
    fun `should exclude deleted, cancelled and notifications already associated to other invoices`() {
        val invoice = createInvoice()
        val otherInvoice = createInvoice()
        val baseDate = LocalDateTime.now().minusDays(1)

        val valid = createNotification(purchasedAt = baseDate)
        createNotification(purchasedAt = baseDate, deletedAt = LocalDateTime.now())
        createNotification(purchasedAt = baseDate, cancelledAt = LocalDateTime.now())
        createNotification(purchasedAt = baseDate, purchaseInvoiceId = otherInvoice.id)
        val alreadyAssociatedToCurrent = createNotification(
            purchasedAt = baseDate,
            purchaseInvoiceId = invoice.id,
        )

        val result = provider.execute(
            invoiceId = invoice.id!!,
            amount = null,
            startDate = null,
            endDate = null,
        )

        assertTrue(result.isSuccess)
        val ids = result.getOrThrow().map { it.id }.toSet()
        assertTrue(ids.contains(valid.id))
        assertTrue(ids.contains(alreadyAssociatedToCurrent.id))
        assertEquals(2, ids.size)
    }

    @Test
    fun `should limit results to 20`() {
        val invoice = createInvoice()
        val baseDate = LocalDateTime.now().minusDays(1)
        repeat(25) { index ->
            createNotification(purchasedAt = baseDate.minusMinutes(index.toLong()))
        }

        val result = provider.execute(
            invoiceId = invoice.id!!,
            amount = null,
            startDate = null,
            endDate = null,
        )

        assertTrue(result.isSuccess)
        assertEquals(20, result.getOrThrow().size)
    }

    @Test
    fun `should return empty list when no notifications match`() {
        val invoice = createInvoice()

        val result = provider.execute(
            invoiceId = invoice.id!!,
            amount = BigDecimal("9999.99"),
            startDate = null,
            endDate = null,
        )

        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().isEmpty())
    }

    @Test
    fun `should find by amount without date filters even when notification is older than 7 days`() {
        val invoice = createInvoice()
        val oldTarget = createNotification(
            amount = BigDecimal("150.00"),
            purchasedAt = LocalDateTime.now().minusDays(30),
        )

        val result = provider.execute(
            invoiceId = invoice.id!!,
            amount = BigDecimal("150.00"),
            startDate = null,
            endDate = null,
        )

        assertTrue(result.isSuccess)
        val ids = result.getOrThrow().map { it.id }
        assertTrue(ids.contains(oldTarget.id))
    }

    @Test
    fun `should fail when invoice not found`() {
        val result = provider.execute(
            invoiceId = 999999L,
            amount = null,
            startDate = null,
            endDate = null,
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is NoSuchElementException)
    }

    @Test
    fun `should fail when invoice is cancelled`() {
        val invoice = createInvoice(cancelledAt = LocalDateTime.now())

        val result = provider.execute(
            invoiceId = invoice.id!!,
            amount = null,
            startDate = null,
            endDate = null,
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is NoSuchElementException)
    }

    @Test
    fun `should fail when invoice is deleted`() {
        val invoice = createInvoice(deletedAt = LocalDateTime.now())

        val result = provider.execute(
            invoiceId = invoice.id!!,
            amount = null,
            startDate = null,
            endDate = null,
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is NoSuchElementException)
    }
}
