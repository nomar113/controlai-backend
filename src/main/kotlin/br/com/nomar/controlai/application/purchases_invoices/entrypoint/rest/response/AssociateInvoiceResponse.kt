package br.com.nomar.controlai.application.purchases_invoices.entrypoint.rest.response

import java.time.LocalDateTime

data class AssociateInvoiceResponse(
    val invoiceId: Long,
    val paymentNotificationId: Long,
    val associatedAt: LocalDateTime,
)
