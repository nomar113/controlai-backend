package br.com.nomar.controlai.application.billing.converter

import br.com.nomar.controlai.application.billing.entrypoint.database.model.SubscriptionModel
import br.com.nomar.controlai.application.billing.entrypoint.database.model.SubscriptionPlanModel
import br.com.nomar.controlai.application.billing.entrypoint.database.model.SubscriptionStatusModel
import br.com.nomar.controlai.domain.billing.entity.Subscription
import br.com.nomar.controlai.domain.billing.entity.SubscriptionPlan
import br.com.nomar.controlai.domain.billing.entity.SubscriptionStatus
import org.springframework.stereotype.Component

@Component
class SubscriptionConverter {

    fun toEntity(model: SubscriptionModel) = Subscription(
        id = model.id,
        groupId = model.groupId,
        plan = SubscriptionPlan.valueOf(model.plan.name),
        status = SubscriptionStatus.valueOf(model.status.name),
        kiwifyOrderId = model.kiwifyOrderId,
        currentPeriodEnd = model.currentPeriodEnd,
    )

    fun toModel(entity: Subscription) = SubscriptionModel(
        id = entity.id,
        groupId = entity.groupId,
        plan = SubscriptionPlanModel.valueOf(entity.plan.name),
        status = SubscriptionStatusModel.valueOf(entity.status.name),
        kiwifyOrderId = entity.kiwifyOrderId,
        currentPeriodEnd = entity.currentPeriodEnd,
    )
}
