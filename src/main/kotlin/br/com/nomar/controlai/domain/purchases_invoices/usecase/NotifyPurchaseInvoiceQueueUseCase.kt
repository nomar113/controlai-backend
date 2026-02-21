package br.com.nomar.controlai.domain.purchases_invoices.usecase

import br.com.nomar.controlai.application.purchases_invoices.entrypoint.rest.request.PurchaseInvoiceRequest
import br.com.nomar.controlai.domain.purchases_invoices.gateway.NotifyPurchaseInvoiceQueueGateway
import org.springframework.stereotype.Component

@Component
class NotifyPurchaseInvoiceQueueUseCase(
    private val notifyPurchaseInvoiceQueueGateway: NotifyPurchaseInvoiceQueueGateway,
) {
    fun execute(purchaseInvoice: PurchaseInvoiceRequest): Result<Unit> {
        return runCatching {
            notifyPurchaseInvoiceQueueGateway.execute(purchaseInvoice).getOrThrow()
        }
    }
}
