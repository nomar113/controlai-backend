package br.com.nomar.controlai.application.billing.entrypoint.database.repository

import br.com.nomar.controlai.application.billing.entrypoint.database.model.KiwifyWebhookEventModel
import org.springframework.data.jpa.repository.JpaRepository

interface KiwifyWebhookEventRepository : JpaRepository<KiwifyWebhookEventModel, Long> {
    fun findByKiwifyEventId(kiwifyEventId: String): KiwifyWebhookEventModel?
}
