package br.com.nomar.controlai.domain.purchase.usecase

import br.com.nomar.controlai.application.purchase.entrypoint.database.model.Purchase
import br.com.nomar.controlai.domain.purchase.gateway.SavePurchaseFromNotificationGateway
import org.springframework.stereotype.Component

@Component
class SavePurchaseFromNotificationUseCase(
    private val savePurchaseGateway: SavePurchaseFromNotificationGateway,
) {
    fun execute(purchase: Purchase): Result<Purchase> {
        return runCatching {
            savePurchaseGateway.execute(purchase).getOrThrow()
        }
    }
}
