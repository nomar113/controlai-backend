package br.com.nomar.controlai.domain.purchases_invoices.gateway

import br.com.nomar.controlai.domain.purchases_invoices.entity.InvoiceReadResult
import br.com.nomar.controlai.domain.purchases_invoices.entity.ReadOutcome

fun interface ProcessInvoiceReadResultGateway {
    // PROCESSED when the conditional update won; DISCARDED when the invoice is gone or no longer PENDING
    fun execute(invoiceId: Long, result: InvoiceReadResult): Result<ReadOutcome>
}
