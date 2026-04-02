package br.com.nomar.controlai.application.payment_methods

import br.com.nomar.controlai.domain.payment_methods.entity.*
import br.com.nomar.controlai.domain.payment_methods.gateway.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@SpringBootTest
class ProviderIntegrationTest {

    @Autowired private lateinit var jdbcTemplate: JdbcTemplate
    @Autowired private lateinit var saveHolderGateway: SaveHolderGateway
    @Autowired private lateinit var listHoldersGateway: ListHoldersGateway
    @Autowired private lateinit var savePaymentMethodGateway: SavePaymentMethodGateway
    @Autowired private lateinit var listPaymentMethodsGateway: ListPaymentMethodsGateway
    @Autowired private lateinit var findPaymentMethodGateway: FindPaymentMethodGateway
    @Autowired private lateinit var updatePaymentMethodGateway: UpdatePaymentMethodGateway
    @Autowired private lateinit var deactivatePaymentMethodGateway: DeactivatePaymentMethodGateway
    @Autowired private lateinit var saveSubCardGateway: SaveSubCardGateway
    @Autowired private lateinit var updateSubCardGateway: UpdateSubCardGateway
    @Autowired private lateinit var deactivateSubCardGateway: DeactivateSubCardGateway

    @BeforeEach
    fun cleanUp() {
        jdbcTemplate.update("UPDATE payment_notifications SET payment_method_id = NULL, sub_card_id = NULL")
        jdbcTemplate.update("DELETE FROM sub_cards")
        jdbcTemplate.update("DELETE FROM payment_methods")
        jdbcTemplate.update("DELETE FROM holders")
    }

    @Test
    fun `should save and list holders`() {
        val saved = saveHolderGateway.execute(Holder(name = "Ramon")).getOrThrow()
        assertNotNull(saved.id)
        assertEquals("Ramon", saved.name)

        saveHolderGateway.execute(Holder(name = "Aline")).getOrThrow()

        val holders = listHoldersGateway.execute().getOrThrow()
        assertEquals(2, holders.size)
        assertEquals("Aline", holders[0].name) // ordered by name
        assertEquals("Ramon", holders[1].name)
    }

    @Test
    fun `should save and find payment method with sub cards`() {
        val holder = saveHolderGateway.execute(Holder(name = "Ramon")).getOrThrow()

        val pm = PaymentMethod(
            name = "Smiles Infinite",
            type = PaymentMethodType.CREDIT_CARD,
            holderId = holder.id!!,
            brand = "Visa",
            closingDay = 15,
            subCards = listOf(
                SubCard(paymentMethodId = 0, lastFourDigits = "6668", type = SubCardType.PHYSICAL_HOLDER),
            ),
        )

        val saved = savePaymentMethodGateway.execute(pm).getOrThrow()
        assertNotNull(saved.id)
        assertEquals("Smiles Infinite", saved.name)
        assertEquals(PaymentMethodType.CREDIT_CARD, saved.type)
        assertEquals(1, saved.subCards.size)
        assertEquals("6668", saved.subCards[0].lastFourDigits)

        val found = findPaymentMethodGateway.execute(saved.id!!).getOrThrow()
        assertEquals("Smiles Infinite", found.name)
        assertNotNull(found.holder)
        assertEquals("Ramon", found.holder?.name)
    }

    @Test
    fun `should list payment methods filtered by holder`() {
        val ramon = saveHolderGateway.execute(Holder(name = "Ramon")).getOrThrow()
        val aline = saveHolderGateway.execute(Holder(name = "Aline")).getOrThrow()

        savePaymentMethodGateway.execute(
            PaymentMethod(name = "Smiles", type = PaymentMethodType.CREDIT_CARD, holderId = ramon.id!!)
        ).getOrThrow()
        savePaymentMethodGateway.execute(
            PaymentMethod(name = "Nubank", type = PaymentMethodType.CREDIT_CARD, holderId = aline.id!!)
        ).getOrThrow()

        val all = listPaymentMethodsGateway.execute(null).getOrThrow()
        assertEquals(2, all.size)

        val ramonOnly = listPaymentMethodsGateway.execute(ramon.id).getOrThrow()
        assertEquals(1, ramonOnly.size)
        assertEquals("Smiles", ramonOnly[0].name)
    }

    @Test
    fun `should update payment method`() {
        val holder = saveHolderGateway.execute(Holder(name = "Ramon")).getOrThrow()
        val saved = savePaymentMethodGateway.execute(
            PaymentMethod(name = "Old Name", type = PaymentMethodType.CREDIT_CARD, holderId = holder.id!!)
        ).getOrThrow()

        val updated = updatePaymentMethodGateway.execute(
            PaymentMethod(id = saved.id, name = "New Name", type = PaymentMethodType.CREDIT_CARD, holderId = holder.id!!, brand = "Mastercard")
        ).getOrThrow()

        assertEquals("New Name", updated.name)
        assertEquals("Mastercard", updated.brand)
    }

    @Test
    fun `should soft delete payment method and hide from listing`() {
        val holder = saveHolderGateway.execute(Holder(name = "Ramon")).getOrThrow()
        val saved = savePaymentMethodGateway.execute(
            PaymentMethod(name = "To Delete", type = PaymentMethodType.PIX, holderId = holder.id!!)
        ).getOrThrow()

        assertEquals(1, listPaymentMethodsGateway.execute(null).getOrThrow().size)

        deactivatePaymentMethodGateway.execute(saved.id!!).getOrThrow()

        val afterDelete = listPaymentMethodsGateway.execute(null).getOrThrow()
        assertEquals(0, afterDelete.size)

        // Verify still in DB via raw query
        val rawCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM payment_methods WHERE id = ?", Int::class.java, saved.id
        )
        assertEquals(1, rawCount)
    }

    @Test
    fun `should save and update sub card`() {
        val holder = saveHolderGateway.execute(Holder(name = "Ramon")).getOrThrow()
        val pm = savePaymentMethodGateway.execute(
            PaymentMethod(name = "Smiles", type = PaymentMethodType.CREDIT_CARD, holderId = holder.id!!)
        ).getOrThrow()

        val sc = saveSubCardGateway.execute(
            SubCard(paymentMethodId = pm.id!!, lastFourDigits = "6668", type = SubCardType.PHYSICAL_HOLDER)
        ).getOrThrow()

        assertNotNull(sc.id)
        assertEquals("6668", sc.lastFourDigits)

        val updated = updateSubCardGateway.execute(
            SubCard(id = sc.id, paymentMethodId = pm.id!!, lastFourDigits = "9999", type = SubCardType.VIRTUAL, nickname = "Virtual Compras")
        ).getOrThrow()

        assertEquals("9999", updated.lastFourDigits)
        assertEquals(SubCardType.VIRTUAL, updated.type)
        assertEquals("Virtual Compras", updated.nickname)
    }

    @Test
    fun `should soft delete sub card without affecting parent`() {
        val holder = saveHolderGateway.execute(Holder(name = "Ramon")).getOrThrow()
        val pm = savePaymentMethodGateway.execute(
            PaymentMethod(name = "Smiles", type = PaymentMethodType.CREDIT_CARD, holderId = holder.id!!)
        ).getOrThrow()

        val sc = saveSubCardGateway.execute(
            SubCard(paymentMethodId = pm.id!!, lastFourDigits = "6668", type = SubCardType.PHYSICAL_HOLDER)
        ).getOrThrow()

        deactivateSubCardGateway.execute(sc.id!!).getOrThrow()

        // Sub card should not appear in parent's sub cards
        val found = findPaymentMethodGateway.execute(pm.id!!).getOrThrow()
        assertEquals(0, found.subCards.size)

        // Parent should still exist
        assertEquals("Smiles", found.name)
    }

    @Test
    fun `should save sub card with digital wallet platform`() {
        val holder = saveHolderGateway.execute(Holder(name = "Ramon")).getOrThrow()
        val pm = savePaymentMethodGateway.execute(
            PaymentMethod(name = "Smiles", type = PaymentMethodType.CREDIT_CARD, holderId = holder.id!!)
        ).getOrThrow()

        val sc = saveSubCardGateway.execute(
            SubCard(
                paymentMethodId = pm.id!!,
                lastFourDigits = "1234",
                type = SubCardType.DIGITAL_WALLET,
                walletPlatform = WalletPlatform.APPLE_PAY,
            )
        ).getOrThrow()

        assertEquals(SubCardType.DIGITAL_WALLET, sc.type)
        assertEquals(WalletPlatform.APPLE_PAY, sc.walletPlatform)
    }

    @Test
    fun `should save sub card with dependent name`() {
        val holder = saveHolderGateway.execute(Holder(name = "Ramon")).getOrThrow()
        val pm = savePaymentMethodGateway.execute(
            PaymentMethod(name = "Smiles", type = PaymentMethodType.CREDIT_CARD, holderId = holder.id!!)
        ).getOrThrow()

        val sc = saveSubCardGateway.execute(
            SubCard(
                paymentMethodId = pm.id!!,
                lastFourDigits = "9687",
                type = SubCardType.PHYSICAL_DEPENDENT,
                dependentName = "Aline",
            )
        ).getOrThrow()

        assertEquals(SubCardType.PHYSICAL_DEPENDENT, sc.type)
        assertEquals("Aline", sc.dependentName)
    }
}
