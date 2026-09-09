package br.com.nomar.controlai.domain.billing.entity

import java.time.Instant

data class KiwifyWebhookEvent(
    val id: Long? = null,
    val kiwifyEventId: String,
    val orderStatus: String,
    val rawPayload: String,
    val processedAt: Instant? = null,
)
