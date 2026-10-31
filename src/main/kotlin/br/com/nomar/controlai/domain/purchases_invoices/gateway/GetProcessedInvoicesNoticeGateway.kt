package br.com.nomar.controlai.domain.purchases_invoices.gateway

fun interface GetProcessedInvoicesNoticeGateway {
    // Invoices of the caller's group processed after the caller last saw (or dismissed) the notice
    fun execute(): Result<Int>
}
