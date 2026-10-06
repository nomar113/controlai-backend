package br.com.nomar.controlai.config

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
 * Validates Flyway migration V44 against data that existed before it: active duplicates (same
 * group_id + access_key) must be soft deleted keeping the lowest id, otherwise the unique index
 * would fail to be created, and every existing row must become PROCESSED.
 *
 * The main test schema is already at the latest version, so this test migrates a scratch schema
 * on the same Testcontainers MySQL up to V43, seeds the duplicates and then applies V44.
 */
@SpringBootTest
class PurchaseInvoicePendingStatusMigrationIntegrationTest {

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    private lateinit var rootJdbc: JdbcTemplate
    private lateinit var scratchDataSource: DriverManagerDataSource
    private lateinit var scratchJdbc: JdbcTemplate

    @BeforeEach
    fun setUp() {
        // The test user only has grants on the main schema, so the scratch schema is created as root.
        // MySQLContainer sets MYSQL_ROOT_PASSWORD to the same password as the default user ("test").
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
    fun `V44 keeps only the lowest id of each active duplicate and marks every existing row as PROCESSED`() {
        migrateScratchSchemaTo("43")
        scratchJdbc.update("INSERT INTO `groups` (name) VALUES ('Grupo A V44'), ('Grupo B V44')")
        val (groupA, groupB) = scratchJdbc.queryForList(
            "SELECT id FROM `groups` WHERE name IN ('Grupo A V44', 'Grupo B V44') ORDER BY id",
            Long::class.java,
        )

        val keptA = insertInvoice(groupA, KEY_1)
        val duplicateA1 = insertInvoice(groupA, KEY_1)
        val duplicateA2 = insertInvoice(groupA, KEY_1)
        // Already soft deleted before V44: untouched and does not count as a duplicate
        val previouslyDeleted = insertInvoice(groupA, KEY_2, deleted = true)
        val activeAfterDeleted = insertInvoice(groupA, KEY_2)
        // Same key in another group is not a duplicate
        val sameKeyGroupB = insertInvoice(groupB, KEY_1)
        // Invoices without access key (manual entries) are never deduplicated
        val noKey1 = insertInvoice(groupA, null)
        val noKey2 = insertInvoice(groupA, null)
        val deletedAtBefore = deletedAtOf(previouslyDeleted)

        migrateScratchSchemaTo("44")

        listOf(keptA, activeAfterDeleted, sameKeyGroupB, noKey1, noKey2).forEach { id ->
            assertNull(deletedAtOf(id), "invoice $id should stay active")
        }
        listOf(duplicateA1, duplicateA2).forEach { id ->
            assertNotNull(deletedAtOf(id), "duplicate invoice $id should be soft deleted")
        }
        assertEquals(deletedAtBefore, deletedAtOf(previouslyDeleted))

        val statuses = scratchJdbc.queryForList("SELECT DISTINCT status FROM purchase_invoices", String::class.java)
        assertEquals(listOf("PROCESSED"), statuses)

        val activeKeys = scratchJdbc.queryForList(
            "SELECT id, active_access_key FROM purchase_invoices WHERE id IN (?, ?, ?)",
            keptA, duplicateA1, previouslyDeleted,
        ).associate { (it["id"] as Number).toLong() to it["active_access_key"] }
        assertEquals(mapOf(keptA to KEY_1, duplicateA1 to null, previouslyDeleted to null), activeKeys)
    }

    @Test
    fun `V44 widens invoice_url to 1024 characters`() {
        migrateScratchSchemaTo("44")

        val maxLength = scratchJdbc.queryForObject(
            """
            SELECT CHARACTER_MAXIMUM_LENGTH FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'purchase_invoices' AND COLUMN_NAME = 'invoice_url'
            """,
            Long::class.java,
        )
        assertEquals(1024L, maxLength)
    }

    private fun migrateScratchSchemaTo(version: String) {
        Flyway.configure()
            .dataSource(scratchDataSource)
            .locations("classpath:db/migration")
            .target(MigrationVersion.fromVersion(version))
            .load()
            .migrate()
    }

    private fun insertInvoice(groupId: Long, accessKey: String?, deleted: Boolean = false): Long {
        val keyHolder = GeneratedKeyHolder()
        scratchJdbc.update({ connection ->
            connection.prepareStatement(
                """
                INSERT INTO purchase_invoices (group_id, date, merchant_name, access_key, deleted_at)
                VALUES (?, '2026-09-01 12:00:00', 'V44 Probe', ?, IF(?, '2026-09-02 12:00:00', NULL))
                """,
                Statement.RETURN_GENERATED_KEYS,
            ).apply {
                setLong(1, groupId)
                setString(2, accessKey)
                setBoolean(3, deleted)
            }
        }, keyHolder)
        return keyHolder.key!!.toLong()
    }

    private fun deletedAtOf(id: Long): Any? =
        scratchJdbc.queryForObject("SELECT deleted_at FROM purchase_invoices WHERE id = ?", Any::class.java, id)

    companion object {
        private const val SCRATCH_SCHEMA = "controlai_v44_probe"
        private const val ROOT_USER = "root"
        private const val ROOT_PASSWORD = "test"
        // Same session time zone as the main test datasource, so TIMESTAMP values are not shifted
        private val URL_PARAMS = TestcontainersDatasourcePostProcessor.DATASOURCE_URL.substringAfter("TC_DAEMON=true&")
        private val SERVER_URL_REGEX = Regex("^jdbc:mysql://[^/]+/")
        private const val KEY_1 = "33260900000000000000650010000000011000000016"
        private const val KEY_2 = "33260900000000000000650010000000021000000025"
    }
}
