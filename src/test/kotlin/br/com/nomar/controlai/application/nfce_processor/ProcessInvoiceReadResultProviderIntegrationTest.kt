package br.com.nomar.controlai.application.nfce_processor

import br.com.nomar.controlai.application.nfce_processor.application.ProcessInvoiceReadResultProvider
import br.com.nomar.controlai.application.purchases_invoices.entrypoint.database.repository.PurchaseInvoiceRepository
import br.com.nomar.controlai.config.TestDatabaseCleaner
import br.com.nomar.controlai.domain.purchases_invoices.entity.InvoiceReadData
import br.com.nomar.controlai.domain.purchases_invoices.entity.InvoiceReadResult
import br.com.nomar.controlai.domain.purchases_invoices.entity.InvoiceStatus
import br.com.nomar.controlai.domain.purchases_invoices.entity.PurchaseItem
import br.com.nomar.controlai.domain.purchases_invoices.entity.PurchasePayment
import br.com.nomar.controlai.domain.purchases_invoices.entity.ReadOutcome
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@SpringBootTest
class ProcessInvoiceReadResultProviderIntegrationTest {

    @Autowired private lateinit var provider: ProcessInvoiceReadResultProvider
    @Autowired private lateinit var purchaseInvoiceRepository: PurchaseInvoiceRepository
    @Autowired private lateinit var jdbcTemplate: JdbcTemplate
    @Autowired private lateinit var databaseCleaner: TestDatabaseCleaner

    @BeforeEach
    fun cleanUp() = databaseCleaner.deleteFinancialData()

    @AfterEach
    fun cleanUpAfter() = databaseCleaner.deleteFinancialData()

    private fun insertInvoice(status: String = "PENDING", deleted: Boolean = false, accessKey: String = ACCESS_KEY): Long {
        val scannedAt = LocalDateTime.of(2026, 10, 25, 12, 0)
        jdbcTemplate.update(
            """INSERT INTO purchase_invoices
               (date, invoice_url, access_key, status, group_id, read_attempts, last_read_failure, created_at, deleted_at)
               VALUES (?, ?, ?, ?, 1, 2, 'TIMEOUT', ?, ?)""",
            scannedAt, invoiceUrl(accessKey), accessKey, status, scannedAt, if (deleted) scannedAt else null,
        )
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long::class.java)!!
    }

    private fun result(
        accessKey: String = ACCESS_KEY,
        firstItemName: String = "ARROZ TIPO 1 5KG",
        firstItemUnitPrice: String = "27.90",
    ) = InvoiceReadResult.of(
        InvoiceReadData(
            accessKey = accessKey,
            merchantName = "Supermercado Exemplo",
            cnpj = "12.345.678/0001-90",
            merchantAddress = "Rua Exemplo, 100, Rio de Janeiro",
            totalItems = 2,
            subtotal = BigDecimal("44.88"),
            discount = BigDecimal("1.00"),
            taxes = BigDecimal("5.10"),
            total = BigDecimal("43.88"),
            change = BigDecimal("6.12"),
            issuedAt = ISSUED_AT,
            items = listOf(
                PurchaseItem(firstItemName, "7890000000011", BigDecimal("1.000"), "UN", BigDecimal(firstItemUnitPrice), BigDecimal("27.90")),
                PurchaseItem("FEIJAO CARIOCA 1KG", "7890000000028", BigDecimal("2.000"), "UN", BigDecimal("8.49"), BigDecimal("16.98")),
            ),
            payments = listOf(
                PurchasePayment("Dinheiro", BigDecimal("30.00")),
                PurchasePayment("Cartão de Débito", BigDecimal("20.00")),
            ),
        )
    )

    private fun count(table: String, column: String, invoiceId: Long): Int =
        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM $table WHERE $column = ?", Int::class.java, invoiceId)!!

    private fun itemCount(invoiceId: Long) = count("purchase_items", "purchase_invoice_id", invoiceId)
    private fun paymentCount(invoiceId: Long) = count("purchase_payments", "purchase_invoices_id", invoiceId)

    private fun rawState(invoiceId: Long): Map<String, Any?> = jdbcTemplate.queryForMap(
        "SELECT status, read_attempts, last_read_failure, merchant_name, processed_at FROM purchase_invoices WHERE id = ?",
        invoiceId,
    )

    @Test
    fun `a pending invoice should become a complete PROCESSED purchase`() {
        val id = insertInvoice()
        val before = Instant.now().minusSeconds(5)

        assertEquals(ReadOutcome.PROCESSED, provider.execute(id, result()).getOrThrow())

        val invoice = purchaseInvoiceRepository.findById(id).orElseThrow()
        assertEquals(InvoiceStatus.PROCESSED, invoice.status)
        assertEquals(ISSUED_AT.toInstant(), invoice.date)
        // Raw column, without the JDBC conversion: 14:32:10-03:00 stored as 17:32:10 UTC
        assertEquals("2026-10-20 17:32:10", jdbcTemplate.queryForObject(
            "SELECT DATE_FORMAT(date, '%Y-%m-%d %H:%i:%s') FROM purchase_invoices WHERE id = ?", String::class.java, id,
        ))
        assertEquals("Supermercado Exemplo", invoice.merchantName)
        assertEquals("Rua Exemplo, 100, Rio de Janeiro", invoice.merchantAddress)
        assertEquals("12.345.678/0001-90", invoice.cnpj)
        assertEquals(2, invoice.totalItems)
        assertEquals(BigDecimal("44.88"), invoice.subtotal)
        assertEquals(BigDecimal("1.00"), invoice.discount)
        assertEquals(BigDecimal("5.10"), invoice.taxes)
        assertEquals(BigDecimal("43.88"), invoice.total)
        assertEquals(0, invoice.readAttempts)
        assertEquals(null, invoice.lastReadFailure)
        assertNotNull(invoice.processedAt)
        assertTrue(invoice.processedAt!!.isAfter(before))
        // The link and the access key are kept as scanned
        assertEquals(invoiceUrl(ACCESS_KEY), invoice.invoiceUrl)
        assertEquals(ACCESS_KEY, invoice.accessKey)

        val items = jdbcTemplate.queryForList(
            "SELECT product_name, code, quantity, unit, unit_price, total_price FROM purchase_items WHERE purchase_invoice_id = ? ORDER BY id",
            id,
        )
        assertEquals(
            listOf(
                mapOf("product_name" to "ARROZ TIPO 1 5KG", "code" to "7890000000011", "quantity" to BigDecimal("1.000"),
                    "unit" to "UN", "unit_price" to BigDecimal("27.90"), "total_price" to BigDecimal("27.90")),
                mapOf("product_name" to "FEIJAO CARIOCA 1KG", "code" to "7890000000028", "quantity" to BigDecimal("2.000"),
                    "unit" to "UN", "unit_price" to BigDecimal("8.49"), "total_price" to BigDecimal("16.98")),
            ),
            items,
        )
        val payments = jdbcTemplate.queryForList(
            "SELECT type, value FROM purchase_payments WHERE purchase_invoices_id = ? ORDER BY id", id,
        )
        assertEquals(
            listOf(
                mapOf("type" to "Dinheiro", "value" to BigDecimal("30.00")),
                mapOf("type" to "Cartão de Débito", "value" to BigDecimal("20.00")),
            ),
            payments,
        )
    }

    @Test
    fun `a unit price with more decimals than the column should be rounded half up to cents`() {
        val id = insertInvoice()

        assertEquals(ReadOutcome.PROCESSED, provider.execute(id, result(firstItemUnitPrice = "27.895")).getOrThrow())

        assertEquals(BigDecimal("27.90"), jdbcTemplate.queryForObject(
            "SELECT unit_price FROM purchase_items WHERE purchase_invoice_id = ? ORDER BY id LIMIT 1", BigDecimal::class.java, id,
        ))
    }

    @Test
    fun `a result for a deleted, PROCESSED or READ_FAILED invoice should be DISCARDED without changes`() {
        // Distinct keys: the active key index allows one not deleted invoice per key in the group
        listOf(
            Triple(insertInvoice(deleted = true, accessKey = key(1)), key(1), "PENDING"),
            Triple(insertInvoice(status = "PROCESSED", accessKey = key(2)), key(2), "PROCESSED"),
            Triple(insertInvoice(status = "READ_FAILED", accessKey = key(3)), key(3), "READ_FAILED"),
        ).forEach { (id, accessKey, status) ->
            assertEquals(ReadOutcome.DISCARDED, provider.execute(id, result(accessKey = accessKey)).getOrThrow())
            assertEquals(
                mapOf("status" to status, "read_attempts" to 2, "last_read_failure" to "TIMEOUT", "merchant_name" to null, "processed_at" to null),
                rawState(id),
            )
            assertEquals(0, itemCount(id))
            assertEquals(0, paymentCount(id))
        }
    }

    @Test
    fun `a result with a different access key or for an unknown invoice should be DISCARDED`() {
        val id = insertInvoice()

        assertEquals(ReadOutcome.DISCARDED, provider.execute(id, result(accessKey = OTHER_ACCESS_KEY)).getOrThrow())
        assertEquals(ReadOutcome.DISCARDED, provider.execute(999_999L, result()).getOrThrow())

        assertEquals("PENDING", rawState(id)["status"])
        assertEquals(0, itemCount(id))
    }

    @Test
    fun `a failing item insert should roll everything back and keep the invoice PENDING without items`() {
        val id = insertInvoice()
        // product_name is VARCHAR(255): MySQL strict mode rejects the item after the header was updated
        val tooLongName = "X".repeat(300)

        assertTrue(provider.execute(id, result(firstItemName = tooLongName)).isFailure)

        assertEquals(
            mapOf("status" to "PENDING", "read_attempts" to 2, "last_read_failure" to "TIMEOUT", "merchant_name" to null, "processed_at" to null),
            rawState(id),
        )
        assertEquals(0, itemCount(id))
        assertEquals(0, paymentCount(id))
    }

    @Test
    fun `two concurrent results for the same invoice should process it exactly once`() {
        val id = insertInvoice()
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)

        val outcomes = try {
            val futures = List(2) {
                executor.submit<ReadOutcome> {
                    start.await()
                    provider.execute(id, result()).getOrThrow()
                }
            }
            start.countDown()
            futures.map { it.get(30, TimeUnit.SECONDS) }
        } finally {
            executor.shutdownNow()
        }

        assertEquals(listOf(ReadOutcome.PROCESSED, ReadOutcome.DISCARDED), outcomes.sorted())
        assertEquals("PROCESSED", rawState(id)["status"])
        assertEquals(2, itemCount(id))
        assertEquals(2, paymentCount(id))
    }

    private companion object {
        const val ACCESS_KEY = "33260253358724000682650010000901721678115882"
        const val OTHER_ACCESS_KEY = "33260253358724000682650010000901721678115899"
        val ISSUED_AT: OffsetDateTime = OffsetDateTime.parse("2026-10-20T14:32:10-03:00")

        fun key(n: Int) = "332602533587240006826500100009017216781158" + n.toString().padStart(2, '0')
        fun invoiceUrl(accessKey: String) = "http://www4.fazenda.rj.gov.br/consultaNFCe/QRCode?p=$accessKey|2|1|1|hash"
    }
}
