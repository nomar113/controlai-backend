package br.com.nomar.controlai.domain.billing.gateway

import br.com.nomar.controlai.domain.billing.entity.KiwifyWebhookEvent

fun interface FindKiwifyWebhookEventByIdGateway {
    fun execute(kiwifyEventId: String): Result<KiwifyWebhookEvent?>
}
