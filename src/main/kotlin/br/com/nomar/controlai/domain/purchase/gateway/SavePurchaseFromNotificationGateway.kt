package br.com.nomar.controlai.domain.purchase.gateway

import br.com.nomar.controlai.application.purchase.entrypoint.database.model.Purchase

fun interface SavePurchaseFromNotificationGateway {
    fun execute(
        purchase: Purchase,
    ): Result<Purchase>
}
