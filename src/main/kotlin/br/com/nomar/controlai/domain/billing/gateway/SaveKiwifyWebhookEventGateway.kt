package br.com.nomar.controlai.domain.billing.gateway

import br.com.nomar.controlai.domain.billing.entity.KiwifyWebhookEvent

fun interface SaveKiwifyWebhookEventGateway {
    fun execute(event: KiwifyWebhookEvent): Result<KiwifyWebhookEvent>
}
