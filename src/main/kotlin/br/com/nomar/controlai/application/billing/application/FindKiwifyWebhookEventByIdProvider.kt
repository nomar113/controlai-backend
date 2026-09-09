package br.com.nomar.controlai.application.billing.application

import br.com.nomar.controlai.application.billing.entrypoint.database.repository.KiwifyWebhookEventRepository
import br.com.nomar.controlai.domain.billing.entity.KiwifyWebhookEvent
import br.com.nomar.controlai.domain.billing.gateway.FindKiwifyWebhookEventByIdGateway
import org.springframework.stereotype.Component

@Component
class FindKiwifyWebhookEventByIdProvider(
    private val kiwifyWebhookEventRepository: KiwifyWebhookEventRepository,
) : FindKiwifyWebhookEventByIdGateway {

    override fun execute(kiwifyEventId: String): Result<KiwifyWebhookEvent?> {
        return runCatching {
            kiwifyWebhookEventRepository.findByKiwifyEventId(kiwifyEventId)?.let {
                KiwifyWebhookEvent(
                    id = it.id,
                    kiwifyEventId = it.kiwifyEventId,
                    orderStatus = it.orderStatus,
                    rawPayload = it.rawPayload,
                    processedAt = it.processedAt,
                )
            }
        }
    }
}
