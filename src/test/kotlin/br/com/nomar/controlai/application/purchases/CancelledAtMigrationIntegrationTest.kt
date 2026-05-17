package br.com.nomar.controlai.application.purchases

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import kotlin.test.assertEquals
import kotlin.test.assertNull

@SpringBootTest
class CancelledAtMigrationIntegrationTest {

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Test
    fun `should have cancelled_at column in payment_notifications`() {
        val count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM information_schema.columns WHERE UPPER(table_name) = 'PAYMENT_NOTIFICATIONS' AND UPPER(column_name) = 'CANCELLED_AT'",
            Int::class.java
        )
        assertEquals(1, count)
    }

    @Test
    fun `should have cancelled_at column in purchase_invoices`() {
        val count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM information_schema.columns WHERE UPPER(table_name) = 'PURCHASE_INVOICES' AND UPPER(column_name) = 'CANCELLED_AT'",
            Int::class.java
        )
        assertEquals(1, count)
    }

    @Test
    fun `should accept null cancelled_at for existing records`() {
        // Insert a record and verify cancelled_at defaults to null
        jdbcTemplate.update(
            """INSERT INTO payment_notifications (card_last_digits, purchased_at, amount, merchant_name, number_of_installments, origin, origin_type)
               VALUES ('1234', '2024-01-01 00:00:00', 100.00, 'TestStore', 1, 'NUBANK', 'HTTP_REQUEST')"""
        )
        val cancelledAt = jdbcTemplate.queryForObject(
            "SELECT cancelled_at FROM payment_notifications WHERE merchant_name = 'TestStore'",
            String::class.java
        )
        assertNull(cancelledAt)

        // Cleanup
        jdbcTemplate.update("DELETE FROM payment_notifications WHERE merchant_name = 'TestStore'")
    }
}
