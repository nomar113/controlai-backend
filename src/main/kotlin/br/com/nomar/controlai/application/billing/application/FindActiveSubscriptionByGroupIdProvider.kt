package br.com.nomar.controlai.application.billing.application

import br.com.nomar.controlai.application.billing.converter.SubscriptionConverter
import br.com.nomar.controlai.application.billing.entrypoint.database.model.SubscriptionStatusModel
import br.com.nomar.controlai.application.billing.entrypoint.database.repository.SubscriptionRepository
import br.com.nomar.controlai.domain.billing.entity.Subscription
import br.com.nomar.controlai.domain.billing.gateway.FindActiveSubscriptionByGroupIdGateway
import org.springframework.stereotype.Component

@Component
class FindActiveSubscriptionByGroupIdProvider(
    private val subscriptionRepository: SubscriptionRepository,
    private val converter: SubscriptionConverter,
) : FindActiveSubscriptionByGroupIdGateway {

    override fun execute(groupId: Long): Result<Subscription?> {
        return runCatching {
            subscriptionRepository.findByGroupId(groupId)
                ?.takeIf { it.status == SubscriptionStatusModel.ACTIVE }
                ?.let(converter::toEntity)
        }
    }
}
