package br.com.nomar.controlai.domain.purchase.usecase

import br.com.nomar.controlai.application.purchase.entrypoint.database.model.Purchase
import br.com.nomar.controlai.domain.purchase.gateway.NotifyPurchaseFromNotificationQueueGateway
import org.springframework.stereotype.Component

@Component
class NotifyPurchaseFromNotificationQueueUseCase(
    private val notificationQueueGateway: NotifyPurchaseFromNotificationQueueGateway,
) {
    fun execute(purchase: Purchase): Result<Unit> {
        return runCatching {
            notificationQueueGateway.execute(purchase).getOrThrow()
        }
    }
}