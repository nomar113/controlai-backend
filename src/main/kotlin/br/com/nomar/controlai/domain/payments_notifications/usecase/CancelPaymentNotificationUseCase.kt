package br.com.nomar.controlai.domain.payments_notifications.usecase

import br.com.nomar.controlai.domain.payments_notifications.gateway.CancelPaymentNotificationGateway
import org.springframework.stereotype.Component

@Component
class CancelPaymentNotificationUseCase(
    private val cancelPaymentNotificationGateway: CancelPaymentNotificationGateway,
) {
    fun execute(id: Long): Result<Unit> {
        return runCatching {
            cancelPaymentNotificationGateway.execute(id).getOrThrow()
        }
    }
}
