package br.com.nomar.controlai.application.payments_notification.entrypoint.rest.request

data class AssociateNotificationRequest(
    val purchaseInvoiceId: Long,
)
