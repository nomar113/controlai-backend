package br.com.nomar.controlai.application.billing.entrypoint.rest.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

// The Kiwify webhook schema is not fully documented publicly (Tech Spec: Riscos Conhecidos),
// so every field here is optional and unknown fields are ignored — parsing must never fail
// just because Kiwify adds/renames a field we don't read yet.
@JsonIgnoreProperties(ignoreUnknown = true)
data class KiwifyWebhookPayload(
    @JsonProperty("order_id") val orderId: String? = null,
    @JsonProperty("order_status") val orderStatus: String? = null,
    @JsonProperty("webhook_event_id") val webhookEventId: String? = null,
    val customer: KiwifyWebhookCustomer? = null,
    val product: KiwifyWebhookProduct? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class KiwifyWebhookCustomer(
    val email: String? = null,
    @JsonProperty("full_name") val fullName: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class KiwifyWebhookProduct(
    @JsonProperty("product_id") val productId: String? = null,
)
