package br.com.nomar.controlai.domain.purchases_invoices.gateway

import br.com.nomar.controlai.domain.purchases_invoices.entity.PurchaseInvoice

fun interface SavePurchaseInvoiceGateway {
    fun execute(
        purchaseInvoice: PurchaseInvoice,
    ): Result<PurchaseInvoice>
}