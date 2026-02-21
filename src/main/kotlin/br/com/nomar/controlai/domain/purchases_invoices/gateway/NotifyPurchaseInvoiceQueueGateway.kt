package br.com.nomar.controlai.domain.purchases_invoices.gateway

import br.com.nomar.controlai.application.purchases_invoices.entrypoint.rest.request.PurchaseInvoiceRequest

fun interface NotifyPurchaseInvoiceQueueGateway {
    fun execute(
        purchaseInvoice: PurchaseInvoiceRequest,
    ): Result<Unit>
}
