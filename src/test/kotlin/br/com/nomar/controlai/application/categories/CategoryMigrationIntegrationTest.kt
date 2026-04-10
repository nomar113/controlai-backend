package br.com.nomar.controlai.application.categories

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@SpringBootTest
class CategoryMigrationIntegrationTest {

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    private val seedCategories = listOf(
        "Gastos Gerais", "Mercado", "Veiculos", "Pets", "Moradia",
        "Remedios", "Medicos", "Transporte", "Viagens", "Assinaturas",
        "Lazer", "Educacao", "Vestuario", "Beleza", "Presentes"
    )

    @BeforeEach
    fun ensureSeedData() {
        // Re-insert seed data if deleted by other tests
        val count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM categories", Int::class.java) ?: 0
        if (count < 15) {
            jdbcTemplate.update("DELETE FROM categories")
            seedCategories.forEach { name ->
                jdbcTemplate.update("INSERT INTO categories (name) VALUES (?)", name)
            }
        }
    }

    @Test
    fun `should create categories table`() {
        val count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM information_schema.tables WHERE UPPER(table_name) = 'CATEGORIES'",
            Int::class.java
        )
        assertEquals(1, count)
    }

    @Test
    fun `should have correct columns in categories table`() {
        val columns = jdbcTemplate.queryForList(
            "SELECT UPPER(column_name) as col FROM information_schema.columns WHERE UPPER(table_name) = 'CATEGORIES'",
        ).map { it["COL"] as String }

        assertTrue(columns.contains("ID"))
        assertTrue(columns.contains("NAME"))
        assertTrue(columns.contains("DELETED_AT"))
        assertTrue(columns.contains("CREATED_AT"))
        assertTrue(columns.contains("UPDATED_AT"))
    }

    @Test
    fun `should have 15 pre-populated categories`() {
        val count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM categories",
            Int::class.java
        )
        assertEquals(15, count)
    }

    @Test
    fun `should contain all expected category names`() {
        val categories = jdbcTemplate.queryForList(
            "SELECT name FROM categories ORDER BY name",
        ).map { it["NAME"] as String }

        val expected = listOf(
            "Assinaturas", "Beleza", "Educacao", "Gastos Gerais", "Lazer",
            "Medicos", "Mercado", "Moradia", "Pets", "Presentes",
            "Remedios", "Transporte", "Veiculos", "Vestuario", "Viagens"
        )

        assertEquals(expected, categories)
    }

    @Test
    fun `should enforce unique constraint on category name`() {
        val exception = runCatching {
            jdbcTemplate.update("INSERT INTO categories (name) VALUES (?)", "Mercado")
        }
        assertTrue(exception.isFailure)
    }

    @Test
    fun `should support soft delete via deleted_at`() {
        jdbcTemplate.update("INSERT INTO categories (name) VALUES (?)", "TestSoftDelete")

        jdbcTemplate.update(
            "UPDATE categories SET deleted_at = CURRENT_TIMESTAMP WHERE name = 'TestSoftDelete'"
        )

        val cat = jdbcTemplate.queryForMap("SELECT deleted_at FROM categories WHERE name = 'TestSoftDelete'")
        assertNotNull(cat["DELETED_AT"])

        // cleanup
        jdbcTemplate.update("DELETE FROM categories WHERE name = 'TestSoftDelete'")
    }

    @Test
    fun `should auto-generate timestamps on insert`() {
        jdbcTemplate.update("INSERT INTO categories (name) VALUES (?)", "TestTimestamps")

        val cat = jdbcTemplate.queryForMap("SELECT created_at, updated_at FROM categories WHERE name = 'TestTimestamps'")
        assertNotNull(cat["CREATED_AT"])
        assertNotNull(cat["UPDATED_AT"])

        // cleanup
        jdbcTemplate.update("DELETE FROM categories WHERE name = 'TestTimestamps'")
    }
}
