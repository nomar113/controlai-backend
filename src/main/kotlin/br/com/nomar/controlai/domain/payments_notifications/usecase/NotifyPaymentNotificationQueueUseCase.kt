package br.com.nomar.controlai.domain.payments_notifications.usecase

import br.com.nomar.controlai.application.payments_notification.entrypoint.database.model.PaymentNotification
import br.com.nomar.controlai.domain.payments_notifications.gateway.NotifyPaymentNotificationQueueGateway
import org.springframework.stereotype.Component

@Component
class NotifyPaymentNotificationQueueUseCase(
    private val notificationQueueGateway: NotifyPaymentNotificationQueueGateway,
) {
    fun execute(paymentNotification: PaymentNotification): Result<Unit> {
        return runCatching {
            notificationQueueGateway.execute(paymentNotification).getOrThrow()
        }
    }
}
