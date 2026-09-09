package br.com.nomar.controlai.domain.billing.gateway

import br.com.nomar.controlai.domain.billing.entity.Subscription

fun interface UpsertSubscriptionGateway {
    fun execute(subscription: Subscription): Result<Subscription>
}
