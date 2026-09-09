package br.com.nomar.controlai.domain.billing.gateway

import br.com.nomar.controlai.domain.billing.entity.Subscription

fun interface FindActiveSubscriptionByGroupIdGateway {
    fun execute(groupId: Long): Result<Subscription?>
}
