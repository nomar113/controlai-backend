package br.com.nomar.controlai.application.payments_notification.application

import br.com.nomar.controlai.application.payments_notification.entrypoint.database.model.PaymentNotification
import br.com.nomar.controlai.config.TestDatabaseCleaner
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertNull

// Purchases received automatically (SMS, iPhone shortcut, queue) are matched to a card by its
// last digits only within the purchase's own group (RF2.1–RF2.5), against two real groups.
// Calls the provider directly, like the SQS listener does.
@SpringBootTest
class SubCardGroupIsolationIntegrationTest {

    @Autowired private lateinit var savePaymentNotificationProvider: SavePaymentNotificationProvider
    @Autowired private lateinit var jdbcTemplate: JdbcTemplate
    @Autowired private lateinit var databaseCleaner: TestDatabaseCleaner

    private val groupA = 1L
    private var groupB: Long = 0
    private val digits = "7788"

    @BeforeEach
    fun setUp() {
        databaseCleaner.deleteFinancialData()
        jdbcTemplate.update("INSERT INTO `groups` (name) VALUES ('Grupo B Isolamento Sub-cartao')")
        groupB = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long::class.java)!!
    }

    @AfterEach
    fun tearDown() {
        databaseCleaner.deleteFinancialData()
        jdbcTemplate.update("DELETE FROM `groups` WHERE id = ?", groupB)
    }

    private fun insertCard(groupId: Long, name: String): Long {
        jdbcTemplate.update("INSERT INTO holders (name, group_id) VALUES (?, ?)", "Titular $name", groupId)
        val holderId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long::class.java)!!
        jdbcTemplate.update(
            "INSERT INTO payment_methods (name, type, holder_id, group_id) VALUES (?, 'CREDIT_CARD', ?, ?)",
            name, holderId, groupId,
        )
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long::class.java)!!
    }

    private fun insertSubCard(paymentMethodId: Long, deleted: Boolean = false): Long {
        jdbcTemplate.update(
            "INSERT INTO sub_cards (payment_method_id, last_four_digits, type, deleted_at) VALUES (?, ?, 'FISICO', ?)",
            paymentMethodId, digits, if (deleted) LocalDateTime.of(2026, 9, 1, 0, 0) else null,
        )
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long::class.java)!!
    }

    private fun receiveSmsPurchaseInGroupA(): PaymentNotification =
        savePaymentNotificationProvider.execute(
            PaymentNotification(
                groupId = groupA,
                cardLastDigits = digits,
                purchasedAt = LocalDateTime.of(2026, 9, 10, 15, 0).toInstant(ZoneOffset.UTC),
                amount = BigDecimal("57.30"),
                merchantName = "Mercado Isolamento",
                numberOfInstallments = 1,
                origin = "NUBANK",
                originType = "SMS",
            ),
        ).getOrThrow()

    private fun storedCardOf(notificationId: Long): Pair<Long?, Long?> {
        val row = jdbcTemplate.queryForMap(
            "SELECT group_id, payment_method_id, sub_card_id FROM payment_notifications WHERE id = ?",
            notificationId,
        )
        assertEquals(groupA, (row["group_id"] as Number).toLong(), "purchase must stay in its own group")
        return (row["payment_method_id"] as Number?)?.toLong() to (row["sub_card_id"] as Number?)?.toLong()
    }

    @Test
    fun `digits that only exist in another group leave the purchase saved without a card`() {
        insertSubCard(insertCard(groupB, "Cartao B"))

        val saved = receiveSmsPurchaseInGroupA()

        val (paymentMethodId, subCardId) = storedCardOf(saved.id)
        assertNull(paymentMethodId)
        assertNull(subCardId)
    }

    @Test
    fun `same digits in both groups link the purchase to its own group's card`() {
        val cardA = insertCard(groupA, "Cartao A")
        val subCardA = insertSubCard(cardA)
        insertSubCard(insertCard(groupB, "Cartao B"))

        val saved = receiveSmsPurchaseInGroupA()

        assertEquals(cardA to subCardA, storedCardOf(saved.id))
    }

    @Test
    fun `two sub-cards with the same digits in the group leave the purchase saved without a card`() {
        insertSubCard(insertCard(groupA, "Cartao A1"))
        insertSubCard(insertCard(groupA, "Cartao A2"))

        val saved = receiveSmsPurchaseInGroupA()

        val (paymentMethodId, subCardId) = storedCardOf(saved.id)
        assertNull(paymentMethodId)
        assertNull(subCardId)
    }

    @Test
    fun `deactivated sub-card is not a candidate`() {
        val cardA = insertCard(groupA, "Cartao A")
        insertSubCard(cardA, deleted = true)
        val activeSubCard = insertSubCard(insertCard(groupA, "Cartao A Ativo"))

        val saved = receiveSmsPurchaseInGroupA()

        val (_, subCardId) = storedCardOf(saved.id)
        assertEquals(activeSubCard, subCardId, "the deactivated sub-card must not make the match ambiguous")
    }

    @Test
    fun `sub-card of a deactivated card is not a candidate`() {
        val deactivatedCard = insertCard(groupA, "Cartao A Desativado")
        insertSubCard(deactivatedCard)
        jdbcTemplate.update("UPDATE payment_methods SET deleted_at = NOW() WHERE id = ?", deactivatedCard)

        val saved = receiveSmsPurchaseInGroupA()

        val (paymentMethodId, subCardId) = storedCardOf(saved.id)
        assertNull(paymentMethodId)
        assertNull(subCardId)
    }
}
