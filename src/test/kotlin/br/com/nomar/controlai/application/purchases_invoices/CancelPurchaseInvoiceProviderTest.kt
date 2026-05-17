package br.com.nomar.controlai.application.purchases_invoices

import br.com.nomar.controlai.application.purchases_invoices.application.CancelPurchaseInvoiceProvider
import br.com.nomar.controlai.application.purchases_invoices.entrypoint.database.model.PurchaseInvoiceModel
import br.com.nomar.controlai.application.purchases_invoices.entrypoint.database.repository.PurchaseInvoiceRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.OffsetDateTime
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@SpringBootTest
class CancelPurchaseInvoiceProviderTest {

    @Autowired
    private lateinit var provider: CancelPurchaseInvoiceProvider

    @Autowired
    private lateinit var repository: PurchaseInvoiceRepository

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    private fun createInvoice(cancelledAt: LocalDateTime? = null): PurchaseInvoiceModel {
        return repository.save(
            PurchaseInvoiceModel(
                date = OffsetDateTime.now(),
                merchantName = "Mercado Teste Cancel",
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
    }

    @Test
    fun `should cancel invoice successfully`() {
        val invoice = createInvoice()

        val result = provider.execute(invoice.id!!)

        assertTrue(result.isSuccess)
        val updated = repository.findById(invoice.id!!).get()
        assertNotNull(updated.cancelledAt)

        // Cleanup
        jdbcTemplate.update("DELETE FROM purchase_invoices WHERE id = ?", invoice.id)
    }

    @Test
    fun `should fail when invoice not found`() {
        val result = provider.execute(999999L)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is NoSuchElementException)
    }

    @Test
    fun `should fail when invoice already cancelled`() {
        val invoice = createInvoice(cancelledAt = LocalDateTime.now())

        val result = provider.execute(invoice.id!!)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)

        // Cleanup
        jdbcTemplate.update("DELETE FROM purchase_invoices WHERE id = ?", invoice.id)
    }
}
