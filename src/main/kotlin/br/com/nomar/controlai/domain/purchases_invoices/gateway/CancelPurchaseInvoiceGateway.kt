package br.com.nomar.controlai.domain.purchases_invoices.gateway

fun interface CancelPurchaseInvoiceGateway {
    fun execute(id: Long): Result<Unit>
}
