package br.com.nomar.controlai.application.billing.application

import br.com.nomar.controlai.application.billing.entrypoint.database.model.KiwifyWebhookEventModel
import br.com.nomar.controlai.application.billing.entrypoint.database.repository.KiwifyWebhookEventRepository
import br.com.nomar.controlai.domain.billing.entity.KiwifyWebhookEvent
import br.com.nomar.controlai.domain.billing.gateway.SaveKiwifyWebhookEventGateway
import org.springframework.stereotype.Component

@Component
class SaveKiwifyWebhookEventProvider(
    private val kiwifyWebhookEventRepository: KiwifyWebhookEventRepository,
) : SaveKiwifyWebhookEventGateway {

    override fun execute(event: KiwifyWebhookEvent): Result<KiwifyWebhookEvent> {
        return runCatching {
            val saved = kiwifyWebhookEventRepository.save(
                KiwifyWebhookEventModel(
                    id = event.id,
                    kiwifyEventId = event.kiwifyEventId,
                    orderStatus = event.orderStatus,
                    rawPayload = event.rawPayload,
                    processedAt = event.processedAt,
                )
            )
            KiwifyWebhookEvent(
                id = saved.id,
                kiwifyEventId = saved.kiwifyEventId,
                orderStatus = saved.orderStatus,
                rawPayload = saved.rawPayload,
                processedAt = saved.processedAt,
            )
        }
    }
}
