package br.com.nomar.controlai.application.purchases_invoices

import br.com.nomar.controlai.application.purchases_invoices.application.RegisterPendingInvoiceProvider
import br.com.nomar.controlai.domain.purchases_invoices.entity.InvoiceStatus
import br.com.nomar.controlai.domain.purchases_invoices.entity.value_objects.NfceQrCode
import br.com.nomar.controlai.domain.purchases_invoices.exception.DuplicateInvoiceException
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import kotlin.test.assertIs
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.support.GeneratedKeyHolder
import java.sql.Statement
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@SpringBootTest
class RegisterPendingInvoiceProviderIntegrationTest {

    @Autowired private lateinit var provider: RegisterPendingInvoiceProvider
    @Autowired private lateinit var jdbcTemplate: JdbcTemplate

    private var groupA: Long = 0
    private var groupB: Long = 0

    @BeforeEach
    fun setUp() {
        groupA = createGroup("Grupo A Nota Pendente")
        groupB = createGroup("Grupo B Nota Pendente")
    }

    @AfterEach
    fun tearDown() {
        jdbcTemplate.update("DELETE FROM purchase_invoices WHERE group_id IN (?, ?)", groupA, groupB)
        jdbcTemplate.update("DELETE FROM `groups` WHERE id IN (?, ?)", groupA, groupB)
    }

    @Test
    fun `should store a PENDING invoice with the raw link, the key and the scan time`() {
        val saved = provider.execute(QR_CODE, groupA, SCANNED_AT).getOrThrow()

        val row = jdbcTemplate.queryForMap(
            """SELECT group_id, status, invoice_url, access_key, date, merchant_name, total, total_items
               FROM purchase_invoices WHERE id = ?""",
            saved.id,
        )
        assertEquals(groupA, (row["group_id"] as Number).toLong())
        assertEquals(InvoiceStatus.PENDING.name, row["status"])
        assertEquals(QR_CONTENT, row["invoice_url"])
        assertEquals(ACCESS_KEY, row["access_key"])
        assertEquals(SCANNED_AT, saved.date)
        assertNull(row["merchant_name"])
        assertNull(row["total"])
        assertNull(row["total_items"])
    }

    @Test
    fun `should refuse the same key twice in the same group`() {
        provider.execute(QR_CODE, groupA, SCANNED_AT).getOrThrow()

        val result = provider.execute(QR_CODE, groupA, SCANNED_AT)

        assertIs<DuplicateInvoiceException>(result.exceptionOrNull())
        assertEquals(1, countRows(groupA))
    }

    @Test
    fun `should allow the same key in another group`() {
        provider.execute(QR_CODE, groupA, SCANNED_AT).getOrThrow()

        val result = provider.execute(QR_CODE, groupB, SCANNED_AT)

        assertTrue(result.isSuccess)
        assertEquals(1, countRows(groupA))
        assertEquals(1, countRows(groupB))
    }

    @Test
    fun `should allow registering again after the invoice is soft deleted`() {
        val first = provider.execute(QR_CODE, groupA, SCANNED_AT).getOrThrow()
        jdbcTemplate.update("UPDATE purchase_invoices SET deleted_at = NOW() WHERE id = ?", first.id)

        val result = provider.execute(QR_CODE, groupA, SCANNED_AT)

        assertTrue(result.isSuccess)
        assertEquals(1, countRows(groupA))
    }

    @Test
    fun `two concurrent scans of the same key store exactly one invoice`() {
        val executor = Executors.newFixedThreadPool(2)
        val start = CountDownLatch(1)
        try {
            val futures = List(2) {
                executor.submit<Result<*>> {
                    start.await()
                    provider.execute(QR_CODE, groupA, SCANNED_AT)
                }
            }
            start.countDown()
            val results = futures.map { it.get(30, TimeUnit.SECONDS) }

            assertEquals(1, results.count { it.isSuccess })
            val failures = results.mapNotNull { it.exceptionOrNull() }
            assertEquals(1, failures.size)
            assertIs<DuplicateInvoiceException>(failures.single())
            assertEquals(1, countRows(groupA))
        } finally {
            executor.shutdownNow()
        }
    }

    private fun countRows(groupId: Long): Int = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM purchase_invoices WHERE group_id = ? AND access_key = ? AND deleted_at IS NULL",
        Int::class.java, groupId, ACCESS_KEY,
    )!!

    private fun createGroup(name: String): Long {
        val keyHolder = GeneratedKeyHolder()
        jdbcTemplate.update({ connection ->
            connection.prepareStatement("INSERT INTO `groups` (name) VALUES (?)", Statement.RETURN_GENERATED_KEYS)
                .apply { setString(1, name) }
        }, keyHolder)
        return keyHolder.key!!.toLong()
    }

    private companion object {
        const val ACCESS_KEY = "33260253358724000682650010000901721678115882"
        const val QR_CONTENT = "http://www4.fazenda.rj.gov.br/consultaNFCe/QRCode?p=$ACCESS_KEY|2|1|1|c77e3a5c4f7a9ad7d25fee080cac222faac1219d"
        val QR_CODE: NfceQrCode = NfceQrCode.parse(QR_CONTENT)
        val SCANNED_AT: Instant = Instant.parse("2026-10-04T15:30:00Z")
    }
}
