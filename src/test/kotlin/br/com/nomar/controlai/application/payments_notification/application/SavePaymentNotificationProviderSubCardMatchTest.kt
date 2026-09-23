package br.com.nomar.controlai.application.payments_notification.application

import br.com.nomar.controlai.application.installments.application.CreateInstallmentsProvider
import br.com.nomar.controlai.application.payment_methods.entrypoint.database.model.SubCardModel
import br.com.nomar.controlai.application.payment_methods.entrypoint.database.repository.SubCardRepository
import br.com.nomar.controlai.application.payments_notification.entrypoint.database.model.PaymentNotification
import br.com.nomar.controlai.application.payments_notification.entrypoint.database.repository.PaymentNotificationRepository
import br.com.nomar.controlai.domain.budget.gateway.EnsureFutureBudgetGateway
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertNull

// Unit tests of the automatic card matching (RF2.1–RF2.5): the lookup is always scoped to the
// purchase's group and only a single candidate is linked.
class SavePaymentNotificationProviderSubCardMatchTest {

    private val groupId = 7L
    private val digits = "4321"

    private val paymentNotificationRepository: PaymentNotificationRepository = mock<PaymentNotificationRepository>().also {
        `when`(it.save(any(PaymentNotification::class.java))).thenAnswer { invocation -> invocation.arguments[0] }
    }
    private val subCardRepository: SubCardRepository = mock()
    private val meterRegistry = SimpleMeterRegistry()
    private val provider = SavePaymentNotificationProvider(
        paymentNotificationRepository = paymentNotificationRepository,
        subCardRepository = subCardRepository,
        createInstallmentsProvider = mock(CreateInstallmentsProvider::class.java),
        ensureFutureBudgetGateway = EnsureFutureBudgetGateway { _, _ -> Result.success(1L) },
        meterRegistry = meterRegistry,
    )

    private fun notification(paymentMethodId: Long? = null) = PaymentNotification(
        groupId = groupId,
        cardLastDigits = digits,
        purchasedAt = LocalDateTime.of(2026, 9, 1, 12, 0).toInstant(ZoneOffset.UTC),
        amount = BigDecimal("42.00"),
        merchantName = "Padaria",
        numberOfInstallments = 1,
        origin = "NUBANK",
        originType = "SMS",
        paymentMethodId = paymentMethodId,
    )

    private fun subCard(id: Long, paymentMethodId: Long) = SubCardModel(
        id = id,
        paymentMethodId = paymentMethodId,
        lastFourDigits = digits,
        type = "FISICO",
    )

    private fun givenCandidatesInGroup(vararg subCards: SubCardModel) {
        `when`(subCardRepository.findActiveByLastFourDigitsAndGroupId(digits, groupId)).thenReturn(subCards.toList())
    }

    private fun matchCount(result: String): Double =
        meterRegistry.find("payment_notification.subcard.match").tag("result", result).counter()?.count() ?: 0.0

    @Test
    fun `single candidate in the group links the purchase to it`() {
        givenCandidatesInGroup(subCard(id = 11, paymentMethodId = 101))

        val saved = provider.execute(notification()).getOrThrow()

        assertEquals(101L, saved.paymentMethodId)
        assertEquals(11L, saved.subCardId)
        assertEquals(1.0, matchCount("matched"))
    }

    @Test
    fun `no candidate in the group leaves the purchase without a card`() {
        givenCandidatesInGroup()

        val saved = provider.execute(notification()).getOrThrow()

        assertNull(saved.paymentMethodId)
        assertNull(saved.subCardId)
        assertEquals(1.0, matchCount("none"))
    }

    @Test
    fun `two candidates in the group leave the purchase without a card`() {
        givenCandidatesInGroup(subCard(id = 11, paymentMethodId = 101), subCard(id = 12, paymentMethodId = 102))

        val saved = provider.execute(notification()).getOrThrow()

        assertNull(saved.paymentMethodId)
        assertNull(saved.subCardId)
        assertEquals(1.0, matchCount("ambiguous"))
    }

    @Test
    fun `lookup is scoped to the purchase's group`() {
        // Real cross-group isolation (a candidate only in another group) is covered against the
        // database in SubCardGroupIsolationIntegrationTest; here we only pin the group passed on.
        givenCandidatesInGroup()

        provider.execute(notification()).getOrThrow()

        verify(subCardRepository).findActiveByLastFourDigitsAndGroupId(digits, groupId)
    }

    @Test
    fun `purchase with a payment method already set does not look up sub-cards`() {
        val saved = provider.execute(notification(paymentMethodId = 55)).getOrThrow()

        assertEquals(55L, saved.paymentMethodId)
        verify(subCardRepository, never()).findActiveByLastFourDigitsAndGroupId(anyString(), anyLong())
        assertNull(meterRegistry.find("payment_notification.subcard.match").counter())
    }
}
