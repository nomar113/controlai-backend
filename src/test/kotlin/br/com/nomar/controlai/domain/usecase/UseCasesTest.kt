package br.com.nomar.controlai.domain.usecase

import br.com.nomar.controlai.application.payments_notification.entrypoint.database.model.PaymentNotification
import br.com.nomar.controlai.application.payments_notification.entrypoint.queue.model.PaymentNotificationQueueMessage
import br.com.nomar.controlai.domain.payments_notifications.gateway.NotifyPaymentNotificationQueueGateway
import br.com.nomar.controlai.domain.payments_notifications.gateway.SavePaymentNotificationGateway
import br.com.nomar.controlai.domain.payments_notifications.usecase.NotifyPaymentNotificationQueueUseCase
import br.com.nomar.controlai.domain.payments_notifications.usecase.SavePaymentNotificationUseCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.ZoneOffset

class UseCasesTest {

    @Test
    fun `save payment notification use case should return success`() {
        val notification = sampleNotification()
        val gateway = SavePaymentNotificationGateway { Result.success(it.copy(id = 10)) }
        val useCase = SavePaymentNotificationUseCase(gateway)

        val result = useCase.execute(notification)

        assertTrue(result.isSuccess)
        assertEquals(10, result.getOrNull()?.id)
    }

    @Test
    fun `notify payment notification queue use case should return failure`() {
        val gateway = NotifyPaymentNotificationQueueGateway {
            Result.failure(IllegalStateException("queue unavailable"))
        }
        val useCase = NotifyPaymentNotificationQueueUseCase(gateway)

        val result = useCase.execute(sampleQueueMessage())

        assertTrue(result.isFailure)
        assertEquals("queue unavailable", result.exceptionOrNull()?.message)
    }

    private fun sampleNotification() = PaymentNotification(
        cardLastDigits = "1234",
        purchasedAt = LocalDateTime.of(2026, 2, 1, 10, 30).toInstant(ZoneOffset.UTC),
        amount = BigDecimal("99.90"),
        merchantName = "Loja",
        numberOfInstallments = 1,
        origin = "app",
        originType = "HTTP_REQUEST",
    )

    private fun sampleQueueMessage() = PaymentNotificationQueueMessage(
        text = "Cartao final 1234",
        origin = "app",
        originType = "HTTP_REQUEST",
    )
}
