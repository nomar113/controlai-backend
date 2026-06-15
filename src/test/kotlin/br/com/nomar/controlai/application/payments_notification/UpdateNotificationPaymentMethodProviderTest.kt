package br.com.nomar.controlai.application.payments_notification

import br.com.nomar.controlai.application.payment_methods.entrypoint.database.model.PaymentMethodModel
import br.com.nomar.controlai.application.payment_methods.entrypoint.database.model.SubCardModel
import br.com.nomar.controlai.application.payment_methods.entrypoint.database.repository.PaymentMethodRepository
import br.com.nomar.controlai.application.payments_notification.application.UpdateNotificationPaymentMethodProvider
import br.com.nomar.controlai.application.payments_notification.entrypoint.database.model.PaymentNotification
import br.com.nomar.controlai.application.payments_notification.entrypoint.database.repository.PaymentNotificationRepository
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.Optional
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UpdateNotificationPaymentMethodProviderTest {

    private val paymentNotificationRepository: PaymentNotificationRepository = mock()
    private val paymentMethodRepository: PaymentMethodRepository = mock()
    private val provider = UpdateNotificationPaymentMethodProvider(
        paymentNotificationRepository,
        paymentMethodRepository,
    )

    private val notificationId = 1L
    private val originalPaymentMethodId = 10L
    private val newPaymentMethodId = 20L
    private val subCardId = 30L
    private val originalCardLastDigits = "1111"
    private val subCardLastDigits = "2222"

    private fun createNotification(
        id: Long = notificationId,
        paymentMethodId: Long? = originalPaymentMethodId,
        subCardId: Long? = null,
        cardLastDigits: String? = originalCardLastDigits,
        cancelledAt: LocalDateTime? = null,
    ) = PaymentNotification(
        id = id,
        cardLastDigits = cardLastDigits,
        purchasedAt = LocalDateTime.of(2024, 6, 15, 14, 0, 0),
        amount = BigDecimal("150.00"),
        merchantName = "Loja Teste",
        numberOfInstallments = 1,
        origin = "NUBANK",
        originType = "HTTP_REQUEST",
        paymentMethodId = paymentMethodId,
        subCardId = subCardId,
        cancelledAt = cancelledAt,
    )

    private fun createSubCard(
        id: Long = subCardId,
        paymentMethodId: Long = newPaymentMethodId,
        lastFourDigits: String = subCardLastDigits,
    ) = SubCardModel(
        id = id,
        paymentMethodId = paymentMethodId,
        lastFourDigits = lastFourDigits,
        type = "FISICO",
    )

    private fun createPaymentMethod(
        id: Long = newPaymentMethodId,
        subCards: List<SubCardModel> = emptyList(),
    ) = PaymentMethodModel(
        id = id,
        name = "Cartao Teste",
        type = "CREDIT",
        holderId = 1L,
        subCards = subCards,
    )

    @Test
    fun `should update payment method without subCard preserving cardLastDigits`() {
        val notification = createNotification()
        val paymentMethod = createPaymentMethod()
        val expectedSaved = notification.copy(
            paymentMethodId = newPaymentMethodId,
            subCardId = null,
            cardLastDigits = originalCardLastDigits,
        )

        `when`(paymentNotificationRepository.findById(notificationId)).thenReturn(Optional.of(notification))
        `when`(paymentMethodRepository.findById(newPaymentMethodId)).thenReturn(Optional.of(paymentMethod))
        `when`(paymentNotificationRepository.save(expectedSaved)).thenReturn(expectedSaved)

        val result = provider.execute(notificationId, newPaymentMethodId, null)

        assertTrue(result.isSuccess)
        val updated = result.getOrThrow()
        assertEquals(newPaymentMethodId, updated.paymentMethodId)
        assertNull(updated.subCardId)
        assertEquals(originalCardLastDigits, updated.cardLastDigits)
        verify(paymentNotificationRepository).save(expectedSaved)
    }

    @Test
    fun `should update payment method with subCard overriding cardLastDigits`() {
        val notification = createNotification()
        val subCard = createSubCard()
        val paymentMethod = createPaymentMethod(subCards = listOf(subCard))
        val expectedSaved = notification.copy(
            paymentMethodId = newPaymentMethodId,
            subCardId = subCardId,
            cardLastDigits = subCardLastDigits,
        )

        `when`(paymentNotificationRepository.findById(notificationId)).thenReturn(Optional.of(notification))
        `when`(paymentMethodRepository.findById(newPaymentMethodId)).thenReturn(Optional.of(paymentMethod))
        `when`(paymentNotificationRepository.save(expectedSaved)).thenReturn(expectedSaved)

        val result = provider.execute(notificationId, newPaymentMethodId, subCardId)

        assertTrue(result.isSuccess)
        val updated = result.getOrThrow()
        assertEquals(newPaymentMethodId, updated.paymentMethodId)
        assertEquals(subCardId, updated.subCardId)
        assertEquals(subCardLastDigits, updated.cardLastDigits)
        verify(paymentNotificationRepository).save(expectedSaved)
    }

    @Test
    fun `should be idempotent when re-confirming the same payment method`() {
        val subCard = createSubCard()
        val paymentMethod = createPaymentMethod(subCards = listOf(subCard))
        val notification = createNotification(
            paymentMethodId = newPaymentMethodId,
            subCardId = subCardId,
            cardLastDigits = subCardLastDigits,
        )
        val expectedSaved = notification.copy(
            paymentMethodId = newPaymentMethodId,
            subCardId = subCardId,
            cardLastDigits = subCardLastDigits,
        )

        `when`(paymentNotificationRepository.findById(notificationId)).thenReturn(Optional.of(notification))
        `when`(paymentMethodRepository.findById(newPaymentMethodId)).thenReturn(Optional.of(paymentMethod))
        `when`(paymentNotificationRepository.save(expectedSaved)).thenReturn(expectedSaved)

        val firstResult = provider.execute(notificationId, newPaymentMethodId, subCardId)
        val secondResult = provider.execute(notificationId, newPaymentMethodId, subCardId)

        assertTrue(firstResult.isSuccess)
        assertTrue(secondResult.isSuccess)
        assertEquals(firstResult.getOrThrow(), secondResult.getOrThrow())
        verify(paymentNotificationRepository, times(2)).save(expectedSaved)
    }

    @Test
    fun `should return failure when notification not found`() {
        `when`(paymentNotificationRepository.findById(notificationId)).thenReturn(Optional.empty())

        val result = provider.execute(notificationId, newPaymentMethodId, null)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is NoSuchElementException)
        assertEquals("PaymentNotification not found: $notificationId", result.exceptionOrNull()?.message)
    }

    @Test
    fun `should return failure when notification is cancelled`() {
        val cancelled = createNotification(cancelledAt = LocalDateTime.now())
        `when`(paymentNotificationRepository.findById(notificationId)).thenReturn(Optional.of(cancelled))

        val result = provider.execute(notificationId, newPaymentMethodId, null)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
        assertEquals("PaymentNotification is cancelled: $notificationId", result.exceptionOrNull()?.message)
    }

    @Test
    fun `should return failure when payment method not found`() {
        val notification = createNotification()
        `when`(paymentNotificationRepository.findById(notificationId)).thenReturn(Optional.of(notification))
        `when`(paymentMethodRepository.findById(newPaymentMethodId)).thenReturn(Optional.empty())

        val result = provider.execute(notificationId, newPaymentMethodId, null)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        assertEquals("PaymentMethod not found: $newPaymentMethodId", result.exceptionOrNull()?.message)
    }

    @Test
    fun `should return failure when subCard does not belong to payment method`() {
        val notification = createNotification()
        val otherSubCard = createSubCard(id = 999L, paymentMethodId = 999L)
        val paymentMethod = createPaymentMethod(subCards = listOf(otherSubCard))

        `when`(paymentNotificationRepository.findById(notificationId)).thenReturn(Optional.of(notification))
        `when`(paymentMethodRepository.findById(newPaymentMethodId)).thenReturn(Optional.of(paymentMethod))

        val result = provider.execute(notificationId, newPaymentMethodId, subCardId)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        assertEquals(
            "SubCard $subCardId does not belong to PaymentMethod $newPaymentMethodId",
            result.exceptionOrNull()?.message,
        )
    }
}
