package br.com.nomar.controlai.domain.purchases_invoices.gateway

import br.com.nomar.controlai.application.purchases_invoices.entrypoint.rest.response.AssociateInvoiceResponse

fun interface AssociateInvoiceGateway {
    fun execute(invoiceId: Long, notificationId: Long): Result<AssociateInvoiceResponse>
}
