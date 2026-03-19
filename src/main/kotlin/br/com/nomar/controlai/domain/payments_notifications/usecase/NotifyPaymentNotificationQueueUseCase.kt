package br.com.nomar.controlai.domain.payments_notifications.usecase

import br.com.nomar.controlai.application.payments_notification.entrypoint.queue.model.PaymentNotificationQueueMessage
import br.com.nomar.controlai.domain.payments_notifications.gateway.NotifyPaymentNotificationQueueGateway
import org.springframework.stereotype.Component

@Component
class NotifyPaymentNotificationQueueUseCase(
    private val notificationQueueGateway: NotifyPaymentNotificationQueueGateway,
) {
    fun execute(paymentNotification: PaymentNotificationQueueMessage): Result<Unit> {
        return runCatching {
            notificationQueueGateway.execute(paymentNotification).getOrThrow()
        }
    }
}
