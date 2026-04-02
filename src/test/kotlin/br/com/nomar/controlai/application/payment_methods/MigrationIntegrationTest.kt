package br.com.nomar.controlai.application.payment_methods

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@SpringBootTest
class MigrationIntegrationTest {

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach
    fun cleanUp() {
        jdbcTemplate.update("UPDATE payment_notifications SET payment_method_id = NULL, sub_card_id = NULL")
        jdbcTemplate.update("DELETE FROM sub_cards")
        jdbcTemplate.update("DELETE FROM payment_methods")
        jdbcTemplate.update("DELETE FROM holders")
        jdbcTemplate.update("DELETE FROM payment_notifications WHERE card_last_digits IN ('9999', '8888')")
    }

    @Test
    fun `should create holders table`() {
        val count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM information_schema.tables WHERE UPPER(table_name) = 'HOLDERS'",
            Int::class.java
        )
        assertEquals(1, count)
    }

    @Test
    fun `should create payment_methods table`() {
        val count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM information_schema.tables WHERE UPPER(table_name) = 'PAYMENT_METHODS'",
            Int::class.java
        )
        assertEquals(1, count)
    }

    @Test
    fun `should create sub_cards table`() {
        val count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM information_schema.tables WHERE UPPER(table_name) = 'SUB_CARDS'",
            Int::class.java
        )
        assertEquals(1, count)
    }

    @Test
    fun `should have payment_method_id and sub_card_id columns in payment_notifications`() {
        val columns = jdbcTemplate.queryForList(
            "SELECT column_name FROM information_schema.columns WHERE UPPER(table_name) = 'PAYMENT_NOTIFICATIONS' AND UPPER(column_name) IN ('PAYMENT_METHOD_ID', 'SUB_CARD_ID')",
        )
        assertEquals(2, columns.size)
    }

    @Test
    fun `should insert and retrieve holder`() {
        jdbcTemplate.update("INSERT INTO holders (name) VALUES (?)", "Ramon")

        val holder = jdbcTemplate.queryForMap("SELECT * FROM holders WHERE name = 'Ramon'")
        assertNotNull(holder["ID"])
        assertEquals("Ramon", holder["NAME"])
        assertNotNull(holder["CREATED_AT"])
    }

    @Test
    fun `should enforce unique constraint on holder name`() {
        jdbcTemplate.update("INSERT INTO holders (name) VALUES (?)", "UniqueTest")

        val exception = runCatching {
            jdbcTemplate.update("INSERT INTO holders (name) VALUES (?)", "UniqueTest")
        }
        assertTrue(exception.isFailure)
    }

    @Test
    fun `should insert payment_method with holder FK`() {
        jdbcTemplate.update("INSERT INTO holders (name) VALUES (?)", "Aline")
        val holderId = jdbcTemplate.queryForObject("SELECT id FROM holders WHERE name = 'Aline'", Long::class.java)

        jdbcTemplate.update(
            "INSERT INTO payment_methods (name, type, holder_id, brand, closing_day) VALUES (?, ?, ?, ?, ?)",
            "Smiles Infinite", "CREDIT_CARD", holderId, "Visa", 15
        )

        val pm = jdbcTemplate.queryForMap("SELECT * FROM payment_methods WHERE name = 'Smiles Infinite'")
        assertNotNull(pm["ID"])
        assertEquals("CREDIT_CARD", pm["TYPE"])
        assertEquals(holderId, pm["HOLDER_ID"])
        assertEquals("Visa", pm["BRAND"])
        assertEquals(15, pm["CLOSING_DAY"])
    }

    @Test
    fun `should insert sub_card with payment_method FK`() {
        jdbcTemplate.update("INSERT INTO holders (name) VALUES (?)", "Ramon")
        val holderId = jdbcTemplate.queryForObject("SELECT id FROM holders WHERE name = 'Ramon'", Long::class.java)

        jdbcTemplate.update(
            "INSERT INTO payment_methods (name, type, holder_id) VALUES (?, ?, ?)",
            "Nubank", "CREDIT_CARD", holderId
        )
        val pmId = jdbcTemplate.queryForObject("SELECT id FROM payment_methods WHERE name = 'Nubank'", Long::class.java)

        jdbcTemplate.update(
            "INSERT INTO sub_cards (payment_method_id, last_four_digits, type) VALUES (?, ?, ?)",
            pmId, "6668", "PHYSICAL_HOLDER"
        )

        val sc = jdbcTemplate.queryForMap("SELECT * FROM sub_cards WHERE last_four_digits = '6668'")
        assertNotNull(sc["ID"])
        assertEquals(pmId, sc["PAYMENT_METHOD_ID"])
        assertEquals("PHYSICAL_HOLDER", sc["TYPE"])
    }

    @Test
    fun `should insert sub_card with digital wallet platform`() {
        jdbcTemplate.update("INSERT INTO holders (name) VALUES (?)", "Ramon")
        val holderId = jdbcTemplate.queryForObject("SELECT id FROM holders WHERE name = 'Ramon'", Long::class.java)

        jdbcTemplate.update(
            "INSERT INTO payment_methods (name, type, holder_id) VALUES (?, ?, ?)",
            "Smiles", "CREDIT_CARD", holderId
        )
        val pmId = jdbcTemplate.queryForObject("SELECT id FROM payment_methods WHERE name = 'Smiles'", Long::class.java)

        jdbcTemplate.update(
            "INSERT INTO sub_cards (payment_method_id, last_four_digits, type, wallet_platform) VALUES (?, ?, ?, ?)",
            pmId, "1234", "DIGITAL_WALLET", "APPLE_PAY"
        )

        val sc = jdbcTemplate.queryForMap("SELECT * FROM sub_cards WHERE last_four_digits = '1234'")
        assertEquals("DIGITAL_WALLET", sc["TYPE"])
        assertEquals("APPLE_PAY", sc["WALLET_PLATFORM"])
    }

    @Test
    fun `should allow null FKs in payment_notifications for backward compatibility`() {
        jdbcTemplate.update(
            """INSERT INTO payment_notifications
               (card_last_digits, purchased_at, amount, merchant_name, number_of_installments, origin, origin_type, payment_method_id, sub_card_id)
               VALUES (?, CURRENT_TIMESTAMP, ?, ?, ?, ?, ?, ?, ?)""",
            "9999", 100.00, "Test Store", 1, "NUBANK", "HTTP_REQUEST", null, null
        )

        val pn = jdbcTemplate.queryForMap(
            "SELECT payment_method_id, sub_card_id FROM payment_notifications WHERE card_last_digits = '9999'"
        )
        assertEquals(null, pn["PAYMENT_METHOD_ID"])
        assertEquals(null, pn["SUB_CARD_ID"])
    }

    @Test
    fun `should allow payment_notifications with valid payment_method FK`() {
        jdbcTemplate.update("INSERT INTO holders (name) VALUES (?)", "Ramon")
        val holderId = jdbcTemplate.queryForObject("SELECT id FROM holders WHERE name = 'Ramon'", Long::class.java)

        jdbcTemplate.update(
            "INSERT INTO payment_methods (name, type, holder_id) VALUES (?, ?, ?)",
            "Nubank", "CREDIT_CARD", holderId
        )
        val pmId = jdbcTemplate.queryForObject("SELECT id FROM payment_methods WHERE name = 'Nubank'", Long::class.java)

        jdbcTemplate.update(
            """INSERT INTO payment_notifications
               (card_last_digits, purchased_at, amount, merchant_name, number_of_installments, origin, origin_type, payment_method_id, sub_card_id)
               VALUES (?, CURRENT_TIMESTAMP, ?, ?, ?, ?, ?, ?, ?)""",
            "8888", 250.00, "Store FK", 1, "NUBANK", "HTTP_REQUEST", pmId, null
        )

        val pn = jdbcTemplate.queryForMap(
            "SELECT payment_method_id FROM payment_notifications WHERE card_last_digits = '8888'"
        )
        assertEquals(pmId, pn["PAYMENT_METHOD_ID"])
    }

    @Test
    fun `should support soft delete via deleted_at on payment_methods`() {
        jdbcTemplate.update("INSERT INTO holders (name) VALUES (?)", "Ramon")
        val holderId = jdbcTemplate.queryForObject("SELECT id FROM holders WHERE name = 'Ramon'", Long::class.java)

        jdbcTemplate.update(
            "INSERT INTO payment_methods (name, type, holder_id) VALUES (?, ?, ?)",
            "SoftDelete Card", "CREDIT_CARD", holderId
        )

        jdbcTemplate.update(
            "UPDATE payment_methods SET deleted_at = CURRENT_TIMESTAMP WHERE name = 'SoftDelete Card'"
        )

        val pm = jdbcTemplate.queryForMap("SELECT deleted_at FROM payment_methods WHERE name = 'SoftDelete Card'")
        assertNotNull(pm["DELETED_AT"])
    }
}
