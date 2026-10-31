package br.com.nomar.controlai.domain.purchases_invoices.gateway

fun interface DismissProcessedInvoicesNoticeGateway {
    // Moves the caller's "notice seen" mark to now, so the notice count goes back to zero
    fun execute(): Result<Unit>
}
