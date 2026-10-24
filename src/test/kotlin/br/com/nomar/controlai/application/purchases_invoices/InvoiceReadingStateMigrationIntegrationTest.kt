package br.com.nomar.controlai.application.purchases_invoices

import br.com.nomar.controlai.config.TestcontainersDatasourcePostProcessor
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.MigrationVersion
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.jdbc.support.GeneratedKeyHolder
import java.sql.Statement

/**
 * Validates Flyway migration V48 (invoice reading state) against data that existed before it: existing invoices
 * start with no failed read, existing users have already "seen" the notice, and new users get it filled too.
 *
 * The main test schema is already at the latest version, so this test migrates a scratch schema on the same
 * Testcontainers MySQL up to V47, seeds rows and then applies V48.
 */
@SpringBootTest
class InvoiceReadingStateMigrationIntegrationTest {

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    private lateinit var rootJdbc: JdbcTemplate
    private lateinit var scratchDataSource: DriverManagerDataSource
    private lateinit var scratchJdbc: JdbcTemplate

    @BeforeEach
    fun setUp() {
        // The test user only has grants on the main schema, so the scratch schema is created as root.
        val serverUrl = jdbcTemplate.dataSource!!.connection.use { connection ->
            SERVER_URL_REGEX.find(connection.metaData.url)!!.value
        }
        rootJdbc = JdbcTemplate(DriverManagerDataSource("$serverUrl?$URL_PARAMS", ROOT_USER, ROOT_PASSWORD))
        rootJdbc.execute("DROP DATABASE IF EXISTS $SCRATCH_SCHEMA")
        rootJdbc.execute("CREATE DATABASE $SCRATCH_SCHEMA")
        scratchDataSource = DriverManagerDataSource("$serverUrl$SCRATCH_SCHEMA?$URL_PARAMS", ROOT_USER, ROOT_PASSWORD)
        scratchJdbc = JdbcTemplate(scratchDataSource)
    }

    @AfterEach
    fun tearDown() {
        rootJdbc.execute("DROP DATABASE IF EXISTS $SCRATCH_SCHEMA")
    }

    @Test
    fun `V48 keeps existing invoices with no failed read and no processed_at`() {
        migrateScratchSchemaTo("47")
        val pendingId = insertInvoice("PENDING")
        val processedId = insertInvoice("PROCESSED")

        migrateScratchSchemaTo("48")

        listOf(pendingId, processedId).forEach { id ->
            val row = scratchJdbc.queryForMap(
                "SELECT status, read_attempts, last_read_failure, processed_at FROM purchase_invoices WHERE id = ?", id,
            )
            assertEquals(0, (row["read_attempts"] as Number).toInt())
            assertNull(row["last_read_failure"])
            assertNull(row["processed_at"])
        }
        assertEquals("PENDING", statusOf(pendingId))
        assertEquals("PROCESSED", statusOf(processedId))
    }

    @Test
    fun `V48 marks the notice as seen for existing users and new users are born with it filled`() {
        migrateScratchSchemaTo("47")
        val existingUser = insertUser("existing@example.com")

        migrateScratchSchemaTo("48")
        val newUser = insertUser("new@example.com")

        assertNotNull(noticeSeenAt(existingUser))
        assertNotNull(noticeSeenAt(newUser))
    }

    @Test
    fun `V48 creates invoice_views keyed by user and invoice, removed with either of them`() {
        migrateScratchSchemaTo("48")
        val userId = insertUser("viewer@example.com")
        val invoiceId = insertInvoice("PROCESSED")
        insertView(userId, invoiceId)
        insertView(userId, insertInvoice("PROCESSED"))

        scratchJdbc.update("DELETE FROM purchase_invoices WHERE id = ?", invoiceId)
        assertEquals(1, countViews(userId))

        scratchJdbc.update("DELETE FROM users WHERE id = ?", userId)
        assertEquals(0, countViews(userId))
    }

    @Test
    fun `V48 indexes purchase_invoices by status and created_at for the processor queue`() {
        migrateScratchSchemaTo("48")

        val columns = scratchJdbc.queryForList(
            """
            SELECT COLUMN_NAME FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'purchase_invoices'
              AND INDEX_NAME = 'idx_purchase_invoices_status_created'
            ORDER BY SEQ_IN_INDEX
            """,
            String::class.java,
        )
        assertEquals(listOf("status", "created_at"), columns)
    }

    // DriverManagerDataSource opens a connection per statement, so LAST_INSERT_ID() would not see the insert
    private fun insertInvoice(status: String): Long {
        val keyHolder = GeneratedKeyHolder()
        scratchJdbc.update({ connection ->
            connection.prepareStatement(
                "INSERT INTO purchase_invoices (group_id, date, access_key, status) VALUES (1, UTC_TIMESTAMP(), NULL, ?)",
                Statement.RETURN_GENERATED_KEYS,
            ).apply { setString(1, status) }
        }, keyHolder)
        return keyHolder.key!!.toLong()
    }

    private fun insertUser(email: String): Long {
        scratchJdbc.update("INSERT INTO users (name, email) VALUES (?, ?)", "Probe", email)
        return scratchJdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long::class.java, email)!!
    }

    private fun insertView(userId: Long, invoiceId: Long) {
        scratchJdbc.update(
            "INSERT INTO invoice_views (user_id, purchase_invoice_id, viewed_at) VALUES (?, ?, NOW(6))",
            userId, invoiceId,
        )
    }

    private fun statusOf(invoiceId: Long): String =
        scratchJdbc.queryForObject("SELECT status FROM purchase_invoices WHERE id = ?", String::class.java, invoiceId)!!

    private fun noticeSeenAt(userId: Long): Any? =
        scratchJdbc.queryForObject("SELECT invoice_notice_seen_at FROM users WHERE id = ?", Any::class.java, userId)

    private fun countViews(userId: Long): Int =
        scratchJdbc.queryForObject("SELECT COUNT(*) FROM invoice_views WHERE user_id = ?", Int::class.java, userId)!!

    private fun migrateScratchSchemaTo(version: String) {
        Flyway.configure()
            .dataSource(scratchDataSource)
            .locations("classpath:db/migration")
            .target(MigrationVersion.fromVersion(version))
            .load()
            .migrate()
    }

    companion object {
        private const val SCRATCH_SCHEMA = "controlai_v48_probe"
        private const val ROOT_USER = "root"
        private const val ROOT_PASSWORD = "test"
        private val URL_PARAMS = TestcontainersDatasourcePostProcessor.DATASOURCE_URL.substringAfter("TC_DAEMON=true&")
        private val SERVER_URL_REGEX = Regex("^jdbc:mysql://[^/]+/")
    }
}
