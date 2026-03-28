package br.com.nomar.controlai.domain.purchases_invoices.gateway

import br.com.nomar.controlai.domain.purchases_invoices.entity.Purchase

fun interface ListPurchasesGateway {
    fun execute(): Result<List<Purchase>>
}
