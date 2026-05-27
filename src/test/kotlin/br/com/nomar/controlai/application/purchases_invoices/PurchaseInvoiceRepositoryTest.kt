package br.com.nomar.controlai.application.purchases_invoices

import br.com.nomar.controlai.application.payments_notification.entrypoint.database.model.PaymentNotification
import br.com.nomar.controlai.application.payments_notification.entrypoint.database.repository.PaymentNotificationRepository
import br.com.nomar.controlai.application.purchases_invoices.entrypoint.database.model.PurchaseInvoiceModel
import br.com.nomar.controlai.application.purchases_invoices.entrypoint.database.repository.PurchaseInvoiceRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@SpringBootTest
@Transactional
class PurchaseInvoiceRepositoryTest {

    @Autowired
    private lateinit var invoiceRepository: PurchaseInvoiceRepository

    @Autowired
    private lateinit var notificationRepository: PaymentNotificationRepository

    private fun createInvoice(
        total: BigDecimal = BigDecimal("100.00"),
        date: OffsetDateTime = OffsetDateTime.now(ZoneOffset.UTC),
        cancelledAt: LocalDateTime? = null,
    ): PurchaseInvoiceModel {
        return invoiceRepository.save(
            PurchaseInvoiceModel(
                date = date,
                merchantName = "Loja Teste Repository",
                merchantAddress = null,
                cnpj = "12.345.678/0001-99",
                totalItems = 2,
                invoiceUrl = null,
                accessKey = null,
                subtotal = total,
                total = total,
                taxes = BigDecimal.ZERO,
                discount = BigDecimal.ZERO,
                cancelledAt = cancelledAt,
            )
        )
    }

    private fun associateInvoiceToNotification(invoiceId: Long) {
        notificationRepository.save(
            PaymentNotification(
                purchasedAt = LocalDateTime.now(),
                amount = BigDecimal("100.00"),
                merchantName = "Loja Teste",
                origin = "MANUAL",
                originType = "MANUAL",
                purchaseInvoiceId = invoiceId,
            )
        )
    }

    @Test
    fun `should return empty list when no invoices match the amount`() {
        createInvoice(total = BigDecimal("200.00"))

        val result = invoiceRepository.findByTotalAndNotAssociated(
            amount = BigDecimal("100.00"),
            purchasedAt = LocalDateTime.now(),
        )

        assertTrue(result.none { it.merchantName == "Loja Teste Repository" })
    }

    @Test
    fun `should return matching invoice when amount matches and not associated`() {
        val invoice = createInvoice(total = BigDecimal("150.00"))

        val result = invoiceRepository.findByTotalAndNotAssociated(
            amount = BigDecimal("150.00"),
            purchasedAt = LocalDateTime.now(),
        )

        assertTrue(result.any { it.id == invoice.id })
    }

    @Test
    fun `should return multiple candidates ordered by date proximity`() {
        val notificationTime = LocalDateTime.of(2025, 6, 15, 14, 0, 0)

        val invoiceFar = createInvoice(
            total = BigDecimal("99.90"),
            date = OffsetDateTime.of(2025, 6, 10, 10, 0, 0, 0, ZoneOffset.UTC),
        )
        val invoiceClose = createInvoice(
            total = BigDecimal("99.90"),
            date = OffsetDateTime.of(2025, 6, 15, 13, 45, 0, 0, ZoneOffset.UTC),
        )
        val invoiceMedium = createInvoice(
            total = BigDecimal("99.90"),
            date = OffsetDateTime.of(2025, 6, 14, 9, 0, 0, 0, ZoneOffset.UTC),
        )

        val result = invoiceRepository.findByTotalAndNotAssociated(
            amount = BigDecimal("99.90"),
            purchasedAt = notificationTime,
        ).filter { it.merchantName == "Loja Teste Repository" }

        assertEquals(3, result.size)
        assertEquals(invoiceClose.id, result[0].id)
        assertEquals(invoiceMedium.id, result[1].id)
        assertEquals(invoiceFar.id, result[2].id)
    }

    @Test
    fun `should exclude invoice already associated to a payment notification`() {
        val associatedInvoice = createInvoice(total = BigDecimal("75.00"))
        val freeInvoice = createInvoice(total = BigDecimal("75.00"))

        associateInvoiceToNotification(associatedInvoice.id!!)

        val result = invoiceRepository.findByTotalAndNotAssociated(
            amount = BigDecimal("75.00"),
            purchasedAt = LocalDateTime.now(),
        ).filter { it.merchantName == "Loja Teste Repository" }

        assertEquals(1, result.size)
        assertEquals(freeInvoice.id, result[0].id)
    }

    @Test
    fun `should exclude cancelled invoice`() {
        createInvoice(total = BigDecimal("50.00"), cancelledAt = LocalDateTime.now())

        val result = invoiceRepository.findByTotalAndNotAssociated(
            amount = BigDecimal("50.00"),
            purchasedAt = LocalDateTime.now(),
        ).filter { it.merchantName == "Loja Teste Repository" }

        assertTrue(result.isEmpty())
    }

    @Test
    fun `should return empty list when all candidates are already associated`() {
        val invoice1 = createInvoice(total = BigDecimal("300.00"))
        val invoice2 = createInvoice(total = BigDecimal("300.00"))

        associateInvoiceToNotification(invoice1.id!!)
        associateInvoiceToNotification(invoice2.id!!)

        val result = invoiceRepository.findByTotalAndNotAssociated(
            amount = BigDecimal("300.00"),
            purchasedAt = LocalDateTime.now(),
        ).filter { it.merchantName == "Loja Teste Repository" }

        assertTrue(result.isEmpty())
    }
}
