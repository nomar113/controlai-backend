package br.com.nomar.controlai.domain.payments_notifications.usecase

import br.com.nomar.controlai.application.payments_notification.entrypoint.database.model.PaymentNotification
import br.com.nomar.controlai.domain.payments_notifications.gateway.SavePaymentNotificationGateway
import org.springframework.stereotype.Component

@Component
class SavePaymentNotificationUseCase(
    private val savePaymentNotificationGateway: SavePaymentNotificationGateway,
) {
    fun execute(paymentNotification: PaymentNotification): Result<PaymentNotification> {
        return runCatching {
            savePaymentNotificationGateway.execute(paymentNotification).getOrThrow()
        }
    }
}
