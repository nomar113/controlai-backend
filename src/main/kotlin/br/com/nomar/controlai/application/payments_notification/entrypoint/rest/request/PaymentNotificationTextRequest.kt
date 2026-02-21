package br.com.nomar.controlai.application.payments_notification.entrypoint.rest.request

import com.fasterxml.jackson.annotation.JsonProperty

data class PaymentNotificationTextRequest(
    val text: String,
    val origin: String,
    @JsonProperty("origin_type")
    val originType: String? = null,
)
