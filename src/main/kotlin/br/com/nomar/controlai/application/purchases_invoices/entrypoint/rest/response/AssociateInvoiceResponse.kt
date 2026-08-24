package br.com.nomar.controlai.application.purchases_invoices.entrypoint.rest.response

import java.time.Instant

data class AssociateInvoiceResponse(
    val invoiceId: Long,
    val paymentNotificationId: Long,
    val associatedAt: Instant,
)
