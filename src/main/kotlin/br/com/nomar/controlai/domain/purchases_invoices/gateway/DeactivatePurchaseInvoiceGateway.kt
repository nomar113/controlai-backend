package br.com.nomar.controlai.domain.purchases_invoices.gateway

fun interface DeactivatePurchaseInvoiceGateway {
    fun execute(id: Long): Result<Unit>
}
