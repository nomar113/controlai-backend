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
class CategoryFkMigrationIntegrationTest {

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach
    fun cleanUp() {
        jdbcTemplate.update("UPDATE payment_notifications SET category_id = NULL")
        jdbcTemplate.update("UPDATE purchase_invoices SET category_id = NULL")
    }

    @Test
    fun `should have category_id column in payment_notifications`() {
        val columns = jdbcTemplate.queryForList(
            "SELECT UPPER(column_name) as col FROM information_schema.columns WHERE UPPER(table_name) = 'PAYMENT_NOTIFICATIONS' AND UPPER(column_name) = 'CATEGORY_ID'",
        )
        assertEquals(1, columns.size)
    }

    @Test
    fun `should have category_id column in purchase_invoices`() {
        val columns = jdbcTemplate.queryForList(
            "SELECT UPPER(column_name) as col FROM information_schema.columns WHERE UPPER(table_name) = 'PURCHASE_INVOICES' AND UPPER(column_name) = 'CATEGORY_ID'",
        )
        assertEquals(1, columns.size)
    }

    @Test
    fun `should insert payment_notification with valid category_id`() {
        jdbcTemplate.update("INSERT INTO categories (name) VALUES (?)", "FkTestCategory")
        val categoryId = jdbcTemplate.queryForObject(
            "SELECT id FROM categories WHERE name = 'FkTestCategory'", Long::class.java
        )

        jdbcTemplate.update(
            """INSERT INTO payment_notifications
               (card_last_digits, purchased_at, amount, merchant_name, number_of_installments, origin, origin_type, category_id)
               VALUES (?, CURRENT_TIMESTAMP, ?, ?, ?, ?, ?, ?)""",
            "7777", 50.00, "Supermercado", 1, "MANUAL", "MANUAL", categoryId
        )

        val pn = jdbcTemplate.queryForMap(
            "SELECT category_id FROM payment_notifications WHERE card_last_digits = '7777'"
        )
        assertEquals(categoryId, pn["CATEGORY_ID"])

        // cleanup
        jdbcTemplate.update("DELETE FROM payment_notifications WHERE card_last_digits = '7777'")
        jdbcTemplate.update("DELETE FROM categories WHERE name = 'FkTestCategory'")
    }

    @Test
    fun `should allow null category_id for backward compatibility`() {
        jdbcTemplate.update(
            """INSERT INTO payment_notifications
               (card_last_digits, purchased_at, amount, merchant_name, number_of_installments, origin, origin_type, category_id)
               VALUES (?, CURRENT_TIMESTAMP, ?, ?, ?, ?, ?, ?)""",
            "6666", 30.00, "Test Store", 1, "MANUAL", "MANUAL", null
        )

        val pn = jdbcTemplate.queryForMap(
            "SELECT category_id FROM payment_notifications WHERE card_last_digits = '6666'"
        )
        assertEquals(null, pn["CATEGORY_ID"])

        // cleanup
        jdbcTemplate.update("DELETE FROM payment_notifications WHERE card_last_digits = '6666'")
    }

    @Test
    fun `should preserve existing category text field`() {
        val columns = jdbcTemplate.queryForList(
            "SELECT UPPER(column_name) as col FROM information_schema.columns WHERE UPPER(table_name) = 'PAYMENT_NOTIFICATIONS' AND UPPER(column_name) = 'CATEGORY'",
        )
        assertEquals(1, columns.size)
    }
}
