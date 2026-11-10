package br.com.nomar.controlai.domain.purchases_invoices.gateway

import br.com.nomar.controlai.domain.purchases_invoices.entity.PendingInvoiceQueue

fun interface ListPendingInvoicesForProcessingGateway {
    // PENDING, not deleted invoices of all groups, fewest failed reads first, then oldest
    // (read_attempts ASC, created_at ASC)
    fun execute(limit: Int): Result<PendingInvoiceQueue>
}
