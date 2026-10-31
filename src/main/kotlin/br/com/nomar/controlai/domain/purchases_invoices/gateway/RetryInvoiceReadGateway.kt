package br.com.nomar.controlai.domain.purchases_invoices.gateway

fun interface RetryInvoiceReadGateway {
    // READ_FAILED -> PENDING with the attempts and the failure reason cleared.
    // Failure: NoSuchElementException when the invoice is not in the caller's group,
    // IllegalStateException when it is not READ_FAILED
    fun execute(invoiceId: Long): Result<Unit>
}
