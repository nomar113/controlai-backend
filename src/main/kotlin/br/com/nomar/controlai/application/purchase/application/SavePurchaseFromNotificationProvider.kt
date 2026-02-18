package br.com.nomar.controlai.application.purchase.application

import br.com.nomar.controlai.application.purchase.entrypoint.database.model.Purchase
import br.com.nomar.controlai.application.purchase.entrypoint.database.repository.PurchaseRepository
import br.com.nomar.controlai.domain.purchase.gateway.SavePurchaseFromNotificationGateway
import org.springframework.stereotype.Component

@Component
class SavePurchaseFromNotificationProvider(
    private val purchaseRepository: PurchaseRepository,
): SavePurchaseFromNotificationGateway {

    override fun execute(purchase: Purchase): Result<Purchase> {
        return runCatching {
            purchaseRepository.save(purchase)
        }
    }
}
