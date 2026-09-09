package br.com.nomar.controlai.application.billing.application

import br.com.nomar.controlai.application.billing.converter.SubscriptionConverter
import br.com.nomar.controlai.application.billing.entrypoint.database.repository.SubscriptionRepository
import br.com.nomar.controlai.domain.billing.entity.Subscription
import br.com.nomar.controlai.domain.billing.gateway.UpsertSubscriptionGateway
import org.springframework.stereotype.Component

@Component
class UpsertSubscriptionProvider(
    private val subscriptionRepository: SubscriptionRepository,
    private val converter: SubscriptionConverter,
) : UpsertSubscriptionGateway {

    override fun execute(subscription: Subscription): Result<Subscription> {
        return runCatching {
            val existingId = subscriptionRepository.findByGroupId(subscription.groupId)?.id
            val model = converter.toModel(subscription.copy(id = existingId))
            val saved = subscriptionRepository.save(model)
            converter.toEntity(saved)
        }
    }
}
