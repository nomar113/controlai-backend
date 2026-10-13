package br.com.nomar.controlai.application.auth

import br.com.nomar.controlai.config.TestcontainersDatasourcePostProcessor
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.MigrationVersion
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.core.io.ClassPathResource
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource

/**
 * Validates Flyway migration V45 (user_identities) against data that existed before it: every users.google_sub
 * becomes a GOOGLE identity, the backfill is idempotent and the unique constraints reject duplicates.
 *
 * The main test schema is already at the latest version, so this test migrates a scratch schema on the same
 * Testcontainers MySQL up to V44, seeds users and then applies V45.
 */
@SpringBootTest
class UserIdentityMigrationIntegrationTest {

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
    fun `V45 creates one GOOGLE identity for each user that has a google_sub and skips the others`() {
        migrateScratchSchemaTo("44")
        val withGoogleA = insertUser("a@example.com", "google-sub-a")
        val withoutGoogle = insertUser("b@example.com", null)
        val withGoogleC = insertUser("c@example.com", "google-sub-c")

        migrateScratchSchemaTo("45")

        val identities = identitiesOrdered()
        assertEquals(
            listOf(
                Identity(withGoogleA, "GOOGLE", "google-sub-a"),
                Identity(withGoogleC, "GOOGLE", "google-sub-c"),
            ),
            identities,
        )
        assertEquals(0, identities.count { it.userId == withoutGoogle })
        val usersWithGoogleSub = scratchJdbc.queryForObject(
            "SELECT COUNT(*) FROM users WHERE google_sub IS NOT NULL",
            Long::class.java,
        )
        assertEquals(usersWithGoogleSub, scratchJdbc.queryForObject(
            "SELECT COUNT(*) FROM user_identities WHERE provider = 'GOOGLE'",
            Long::class.java,
        ))
    }

    @Test
    fun `running the backfill statement again does not duplicate rows`() {
        migrateScratchSchemaTo("44")
        insertUser("a@example.com", "google-sub-a")
        insertUser("c@example.com", "google-sub-c")
        migrateScratchSchemaTo("45")
        val before = identitiesOrdered()

        scratchJdbc.execute(backfillStatement())
        scratchJdbc.execute(backfillStatement())

        assertEquals(before, identitiesOrdered())
    }

    @Test
    fun `V45 keeps google_sub on users`() {
        migrateScratchSchemaTo("45")

        val columnCount = scratchJdbc.queryForObject(
            """
            SELECT COUNT(*) FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users' AND COLUMN_NAME = 'google_sub'
            """,
            Long::class.java,
        )
        assertEquals(1L, columnCount)
    }

    @Test
    fun `unique constraint rejects the same provider and subject for two users`() {
        migrateScratchSchemaTo("45")
        val first = insertUser("a@example.com", null)
        val second = insertUser("b@example.com", null)
        insertIdentity(first, "APPLE", "apple-sub-1")

        assertThrows(DataIntegrityViolationException::class.java) {
            insertIdentity(second, "APPLE", "apple-sub-1")
        }
    }

    @Test
    fun `unique constraint rejects two identities of the same provider for one user`() {
        migrateScratchSchemaTo("45")
        val user = insertUser("a@example.com", null)
        insertIdentity(user, "GOOGLE", "google-sub-1")

        assertThrows(DataIntegrityViolationException::class.java) {
            insertIdentity(user, "GOOGLE", "google-sub-2")
        }
    }

    private data class Identity(val userId: Long, val provider: String, val subject: String)

    private fun identitiesOrdered(): List<Identity> = scratchJdbc.query(
        "SELECT user_id, provider, subject FROM user_identities ORDER BY user_id, provider",
    ) { rs, _ -> Identity(rs.getLong("user_id"), rs.getString("provider"), rs.getString("subject")) }

    private fun insertUser(email: String, googleSub: String?): Long {
        scratchJdbc.update(
            "INSERT INTO users (name, email, google_sub) VALUES (?, ?, ?)",
            "Probe", email, googleSub,
        )
        return scratchJdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long::class.java, email)!!
    }

    private fun insertIdentity(userId: Long, provider: String, subject: String) {
        scratchJdbc.update(
            "INSERT INTO user_identities (user_id, provider, subject) VALUES (?, ?, ?)",
            userId, provider, subject,
        )
    }

    // Takes the backfill INSERT straight from the migration file, so the test never drifts from it
    private fun backfillStatement(): String = ClassPathResource(MIGRATION_PATH)
        .inputStream.bufferedReader().use { it.readText() }
        .split(";")
        .first { it.contains("INSERT INTO user_identities") }

    private fun migrateScratchSchemaTo(version: String) {
        Flyway.configure()
            .dataSource(scratchDataSource)
            .locations("classpath:db/migration")
            .target(MigrationVersion.fromVersion(version))
            .load()
            .migrate()
    }

    companion object {
        private const val SCRATCH_SCHEMA = "controlai_v45_probe"
        private const val MIGRATION_PATH = "db/migration/V45__create_user_identities.sql"
        private const val ROOT_USER = "root"
        private const val ROOT_PASSWORD = "test"
        private val URL_PARAMS = TestcontainersDatasourcePostProcessor.DATASOURCE_URL.substringAfter("TC_DAEMON=true&")
        private val SERVER_URL_REGEX = Regex("^jdbc:mysql://[^/]+/")
    }
}
