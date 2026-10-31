package br.com.nomar.controlai.domain.purchases_invoices.gateway

fun interface MarkInvoiceViewedGateway {
    // Idempotent. Failure: NoSuchElementException when the invoice is not in the caller's group
    fun execute(invoiceId: Long): Result<Unit>
}
