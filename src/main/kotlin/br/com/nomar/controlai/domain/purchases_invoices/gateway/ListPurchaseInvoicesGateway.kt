package br.com.nomar.controlai.domain.purchases_invoices.gateway

import br.com.nomar.controlai.domain.purchases_invoices.entity.PurchaseInvoice

fun interface ListPurchaseInvoicesGateway {
    fun execute(): Result<List<PurchaseInvoice>>
}
