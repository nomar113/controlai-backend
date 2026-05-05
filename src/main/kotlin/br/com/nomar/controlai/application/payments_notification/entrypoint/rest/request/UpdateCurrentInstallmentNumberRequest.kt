package br.com.nomar.controlai.application.payments_notification.entrypoint.rest.request

data class UpdateCurrentInstallmentNumberRequest(
    val numberOfInstallments: Int? = null,
    val currentInstallmentNumber: Int?,
)
