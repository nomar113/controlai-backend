package br.com.nomar.controlai.domain.purchases_invoices.usecase

import br.com.nomar.controlai.domain.purchases_invoices.entity.PurchaseInvoice
import br.com.nomar.controlai.domain.purchases_invoices.gateway.SavePurchaseInvoiceGateway
import org.springframework.stereotype.Component

@Component
class SavePurchaseInvoiceUseCase(
    private val savePurchaseInvoiceGateway: SavePurchaseInvoiceGateway
) {
    fun execute(purchaseInvoice: PurchaseInvoice): Result<PurchaseInvoice> {
        return runCatching {
            savePurchaseInvoiceGateway.execute(purchaseInvoice).getOrThrow()
        }
    }
}