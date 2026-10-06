package br.com.nomar.controlai.config

import br.com.nomar.controlai.application.purchases_invoices.entrypoint.database.model.PurchaseInvoiceModel
import br.com.nomar.controlai.application.purchases_invoices.entrypoint.database.repository.PurchaseInvoiceRepository
import br.com.nomar.controlai.domain.purchases_invoices.entity.InvoiceStatus
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.support.GeneratedKeyHolder
import java.sql.Statement
import java.time.Instant
import java.time.LocalDateTime

/**
 * Validates the unique index uk_purchase_invoices_group_active_key (V44): an access key can be
 * active only once per group, the same key is allowed in another group, and a soft deleted
 * invoice frees its key for a new registration.
 */
@SpringBootTest
class PurchaseInvoiceActiveAccessKeyIndexIntegrationTest {

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    private lateinit var purchaseInvoiceRepository: PurchaseInvoiceRepository

    private var groupA: Long = 0
    private var groupB: Long = 0

    @BeforeEach
    fun setUp() {
        groupA = createGroup("Grupo A Indice Chave Ativa")
        groupB = createGroup("Grupo B Indice Chave Ativa")
    }

    @AfterEach
    fun tearDown() {
        jdbcTemplate.update("DELETE FROM purchase_invoices WHERE group_id IN (?, ?)", groupA, groupB)
        jdbcTemplate.update("DELETE FROM `groups` WHERE id IN (?, ?)", groupA, groupB)
    }

    @Test
    fun `same access key twice in the same group violates the unique index`() {
        purchaseInvoiceRepository.saveAndFlush(pendingInvoice(groupA))

        val error = assertThrows<DataIntegrityViolationException> {
            purchaseInvoiceRepository.saveAndFlush(pendingInvoice(groupA))
        }
        val cause = error.mostSpecificCause.message.orEmpty()
        assertTrue(cause.contains(UNIQUE_INDEX), "unexpected constraint violation: $cause")
    }

    @Test
    fun `same access key in another group is allowed`() {
        purchaseInvoiceRepository.saveAndFlush(pendingInvoice(groupA))
        purchaseInvoiceRepository.saveAndFlush(pendingInvoice(groupB))

        assertEquals(2, countActive(ACCESS_KEY))
    }

    @Test
    fun `soft deleted invoice frees its access key for a new registration`() {
        val first = purchaseInvoiceRepository.saveAndFlush(pendingInvoice(groupA))
        purchaseInvoiceRepository.saveAndFlush(first.copy(deletedAt = LocalDateTime.now()))

        val second = purchaseInvoiceRepository.saveAndFlush(pendingInvoice(groupA))

        assertEquals(1, countActive(ACCESS_KEY))
        assertEquals(ACCESS_KEY, activeAccessKeyOf(second.id!!))
        assertEquals(null, activeAccessKeyOf(first.id!!))
    }

    @Test
    fun `status is persisted as text and defaults to PROCESSED`() {
        val pending = purchaseInvoiceRepository.saveAndFlush(pendingInvoice(groupA))
        val processed = purchaseInvoiceRepository.saveAndFlush(
            pendingInvoice(groupA).copy(accessKey = OTHER_KEY, status = InvoiceStatus.PROCESSED)
        )
        val defaulted = purchaseInvoiceRepository.saveAndFlush(
            PurchaseInvoiceModel(
                groupId = groupA, date = Instant.now(), merchantName = "Manual", merchantAddress = null,
                cnpj = null, totalItems = null, invoiceUrl = null, accessKey = null, subtotal = null,
                total = null, taxes = null, discount = null,
            )
        )

        assertEquals("PENDING", statusOf(pending.id!!))
        assertEquals("PROCESSED", statusOf(processed.id!!))
        assertEquals("PROCESSED", statusOf(defaulted.id!!))
        assertEquals(InvoiceStatus.PENDING, purchaseInvoiceRepository.findByIdAndGroupId(pending.id!!, groupA)?.status)
    }

    @Test
    fun `invoice_url accepts a 1024 character QR Code link`() {
        val longUrl = "https://www.fazenda.rj.gov.br/nfce/consulta?p=$ACCESS_KEY|3|2|".padEnd(1024, 'A')

        val saved = purchaseInvoiceRepository.saveAndFlush(pendingInvoice(groupA).copy(invoiceUrl = longUrl))

        assertEquals(longUrl, purchaseInvoiceRepository.findByIdAndGroupId(saved.id!!, groupA)?.invoiceUrl)
    }

    private fun pendingInvoice(groupId: Long) = PurchaseInvoiceModel(
        groupId = groupId,
        date = Instant.parse("2026-10-03T12:00:00Z"),
        merchantName = null,
        merchantAddress = null,
        cnpj = null,
        totalItems = null,
        invoiceUrl = "https://www.fazenda.rj.gov.br/nfce/consulta?p=$ACCESS_KEY|2|1|1|ABC",
        accessKey = ACCESS_KEY,
        subtotal = null,
        total = null,
        taxes = null,
        discount = null,
        status = InvoiceStatus.PENDING,
    )

    private fun createGroup(name: String): Long {
        val keyHolder = GeneratedKeyHolder()
        jdbcTemplate.update({ connection ->
            connection.prepareStatement("INSERT INTO `groups` (name) VALUES (?)", Statement.RETURN_GENERATED_KEYS)
                .apply { setString(1, name) }
        }, keyHolder)
        return keyHolder.key!!.toLong()
    }

    private fun countActive(accessKey: String): Int = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM purchase_invoices WHERE access_key = ? AND deleted_at IS NULL AND group_id IN (?, ?)",
        Int::class.java, accessKey, groupA, groupB,
    )!!

    private fun activeAccessKeyOf(id: Long): String? = jdbcTemplate.queryForObject(
        "SELECT active_access_key FROM purchase_invoices WHERE id = ?", String::class.java, id,
    )

    private fun statusOf(id: Long): String? = jdbcTemplate.queryForObject(
        "SELECT status FROM purchase_invoices WHERE id = ?", String::class.java, id,
    )

    companion object {
        private const val UNIQUE_INDEX = "uk_purchase_invoices_group_active_key"
        private const val ACCESS_KEY = "33261000000000000000650010000000991000000990"
        private const val OTHER_KEY = "33261000000000000000650010000000981000000981"
    }
}
