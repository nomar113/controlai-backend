package br.com.nomar.controlai.domain.billing.entity

import java.time.Instant

enum class SubscriptionPlan { ANNUAL, LIFETIME, GRANDFATHERED }
enum class SubscriptionStatus { ACTIVE, CANCELLED, EXPIRED }

data class Subscription(
    val id: Long? = null,
    val groupId: Long,
    val plan: SubscriptionPlan,
    val status: SubscriptionStatus,
    val kiwifyOrderId: String? = null,
    val currentPeriodEnd: Instant? = null,
) {
    init {
        val isWithoutExpiry = plan == SubscriptionPlan.LIFETIME || plan == SubscriptionPlan.GRANDFATHERED
        require(!isWithoutExpiry || currentPeriodEnd == null) {
            "currentPeriodEnd deve ser nulo para os planos LIFETIME e GRANDFATHERED"
        }
    }
}
