package br.com.nomar.controlai.config

import br.com.nomar.controlai.application.purchases_invoices.entrypoint.database.model.PurchaseInvoiceModel
import br.com.nomar.controlai.application.purchases_invoices.entrypoint.database.repository.PurchaseInvoiceRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import java.time.Instant

/**
 * Valida a migration Flyway V36 (purchase_invoices.date: datetime -> TIMESTAMP): aplicada
 * sem erro no banco de teste (mesmo pipeline Flyway usado em producao) e sem perda de dados
 * na conversao — um Instant gravado via a entidade ja padronizada (Tarefa 2.0) e lido de volta
 * identico.
 */
@SpringBootTest
class PurchaseInvoiceDateMigrationIntegrationTest {

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    private lateinit var purchaseInvoiceRepository: PurchaseInvoiceRepository

    @AfterEach
    fun tearDown() {
        purchaseInvoiceRepository.deleteAll(
            purchaseInvoiceRepository.findAll().filter { it.merchantName == "Migration Probe" }
        )
    }

    @Test
    fun `V36 migration applied successfully and purchase_invoices date column is TIMESTAMP`() {
        val migrationSuccess = jdbcTemplate.queryForObject(
            "SELECT success FROM flyway_schema_history WHERE version = '36'",
            Boolean::class.java,
        )
        assertTrue(migrationSuccess == true)

        val columnType = jdbcTemplate.queryForObject(
            """
            SELECT DATA_TYPE FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'purchase_invoices' AND COLUMN_NAME = 'date'
            """,
            String::class.java,
        )
        assertEquals("timestamp", columnType)
    }

    @Test
    fun `no data loss when writing and reading purchase_invoices date after the migration`() {
        val known = Instant.parse("2026-01-15T23:30:00Z")
        val saved = purchaseInvoiceRepository.save(
            PurchaseInvoiceModel(
                groupId = 1L,
                date = known,
                merchantName = "Migration Probe",
                merchantAddress = null,
                cnpj = null,
                totalItems = null,
                invoiceUrl = null,
                accessKey = null,
                subtotal = null,
                total = null,
                taxes = null,
                discount = null,
            )
        )

        val reloaded = purchaseInvoiceRepository.findAllByOrderByDateDesc().first { it.id == saved.id }

        assertEquals(known, reloaded.date)
    }
}
