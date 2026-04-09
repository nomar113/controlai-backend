package br.com.nomar.controlai.domain.payments_notifications.usecase

import br.com.nomar.controlai.domain.payments_notifications.gateway.DeactivatePaymentNotificationGateway
import org.springframework.stereotype.Component

@Component
class DeactivatePaymentNotificationUseCase(
    private val deactivatePaymentNotificationGateway: DeactivatePaymentNotificationGateway,
) {
    fun execute(id: Long): Result<Unit> {
        return runCatching {
            deactivatePaymentNotificationGateway.execute(id).getOrThrow()
        }
    }
}
