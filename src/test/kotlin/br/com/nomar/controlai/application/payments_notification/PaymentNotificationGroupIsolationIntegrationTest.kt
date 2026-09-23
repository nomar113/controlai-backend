package br.com.nomar.controlai.application.payments_notification

import br.com.nomar.controlai.application.payments_notification.application.SavePaymentNotificationProvider
import br.com.nomar.controlai.application.payments_notification.entrypoint.database.model.PaymentNotification
import br.com.nomar.controlai.config.TestDatabaseCleaner
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// A purchase never gets another group's card, and another group's purchases never affect this
// group's: manual entries must reference the caller's own card/sub-card, and the duplicate check
// only compares purchases of the same group. MockMvc requests run as group 1 (TestAuthMockMvcConfig).
@SpringBootTest
@AutoConfigureMockMvc
class PaymentNotificationGroupIsolationIntegrationTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var savePaymentNotificationProvider: SavePaymentNotificationProvider
    @Autowired private lateinit var jdbcTemplate: JdbcTemplate
    @Autowired private lateinit var databaseCleaner: TestDatabaseCleaner

    private val groupA = 1L
    private var groupB: Long = 0

    @BeforeEach
    fun setUp() {
        databaseCleaner.deleteFinancialData()
        jdbcTemplate.update("INSERT INTO `groups` (name) VALUES ('Grupo B Isolamento Compras')")
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

    private fun insertSubCard(paymentMethodId: Long, digits: String): Long {
        jdbcTemplate.update(
            "INSERT INTO sub_cards (payment_method_id, last_four_digits, type) VALUES (?, ?, 'FISICO')",
            paymentMethodId, digits,
        )
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long::class.java)!!
    }

    private fun postManual(paymentMethodId: Long?, subCardId: Long? = null) = mockMvc.perform(
        post("/payments/notifications/manual")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                {
                    "merchantName": "Loja Manual",
                    "amount": 80.00,
                    "purchasedAt": "2026-09-10T10:00:00",
                    ${paymentMethodId?.let { "\"paymentMethodId\": $it," } ?: ""}
                    ${subCardId?.let { "\"subCardId\": $it," } ?: ""}
                    "numberOfInstallments": 1
                }
                """.trimIndent(),
            ),
    )

    private fun countNotifications(): Int =
        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM payment_notifications", Int::class.java)!!

    @Test
    fun `manual entry with another group's card is rejected and nothing is saved`() {
        val cardB = insertCard(groupB, "Cartao B")

        postManual(paymentMethodId = cardB).andExpect(status().isBadRequest)

        assertEquals(0, countNotifications())
    }

    @Test
    fun `manual entry with own card but another group's sub-card is rejected`() {
        val cardA = insertCard(groupA, "Cartao A")
        val subCardB = insertSubCard(insertCard(groupB, "Cartao B"), "1234")

        postManual(paymentMethodId = cardA, subCardId = subCardB).andExpect(status().isBadRequest)

        assertEquals(0, countNotifications())
    }

    @Test
    fun `manual entry with a sub-card but no card is rejected`() {
        val subCardA = insertSubCard(insertCard(groupA, "Cartao A"), "1234")

        postManual(paymentMethodId = null, subCardId = subCardA).andExpect(status().isBadRequest)

        assertEquals(0, countNotifications())
    }

    @Test
    fun `manual entry with a deactivated card is rejected`() {
        val cardA = insertCard(groupA, "Cartao A Desativado")
        jdbcTemplate.update("UPDATE payment_methods SET deleted_at = NOW() WHERE id = ?", cardA)

        postManual(paymentMethodId = cardA).andExpect(status().isBadRequest)

        assertEquals(0, countNotifications())
    }

    @Test
    fun `manual entry with a deactivated sub-card is rejected`() {
        val cardA = insertCard(groupA, "Cartao A")
        val subCardA = insertSubCard(cardA, "1234")
        jdbcTemplate.update("UPDATE sub_cards SET deleted_at = NOW() WHERE id = ?", subCardA)

        postManual(paymentMethodId = cardA, subCardId = subCardA).andExpect(status().isBadRequest)

        assertEquals(0, countNotifications())
    }

    @Test
    fun `manual entry with own card and sub-card is saved`() {
        val cardA = insertCard(groupA, "Cartao A")
        val subCardA = insertSubCard(cardA, "1234")

        postManual(paymentMethodId = cardA, subCardId = subCardA)
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.paymentMethodId").value(cardA))
            .andExpect(jsonPath("$.subCardId").value(subCardA))
    }

    private fun smsPurchase(groupId: Long) = PaymentNotification(
        groupId = groupId,
        cardLastDigits = "9090",
        purchasedAt = LocalDateTime.of(2026, 9, 10, 15, 0).toInstant(ZoneOffset.UTC),
        amount = BigDecimal("33.00"),
        merchantName = "Padaria Isolamento",
        numberOfInstallments = 1,
        origin = "NUBANK",
        originType = "SMS",
    )

    @Test
    fun `identical purchase in another group does not block this group's purchase`() {
        savePaymentNotificationProvider.execute(smsPurchase(groupB)).getOrThrow()

        val result = savePaymentNotificationProvider.execute(smsPurchase(groupA))

        assertTrue(result.isSuccess, "a purchase of group B must not count as a duplicate for group A")
        assertEquals(
            listOf(groupA, groupB).sorted(),
            jdbcTemplate.queryForList("SELECT group_id FROM payment_notifications ORDER BY group_id", Long::class.java),
        )
    }

    @Test
    fun `identical purchase in the same group is still rejected as duplicate`() {
        savePaymentNotificationProvider.execute(smsPurchase(groupA)).getOrThrow()

        val result = savePaymentNotificationProvider.execute(smsPurchase(groupA))

        assertTrue(result.isFailure)
        assertEquals(1, countNotifications())
    }
}
