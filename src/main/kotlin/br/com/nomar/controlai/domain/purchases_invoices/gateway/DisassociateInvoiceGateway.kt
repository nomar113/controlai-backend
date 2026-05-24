package br.com.nomar.controlai.domain.purchases_invoices.gateway

fun interface DisassociateInvoiceGateway {
    fun execute(invoiceId: Long): Result<Unit>
}
