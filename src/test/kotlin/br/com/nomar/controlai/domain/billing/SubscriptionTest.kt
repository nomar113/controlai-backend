package br.com.nomar.controlai.domain.billing

import br.com.nomar.controlai.domain.billing.entity.Subscription
import br.com.nomar.controlai.domain.billing.entity.SubscriptionPlan
import br.com.nomar.controlai.domain.billing.entity.SubscriptionStatus
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class SubscriptionTest {

    @Test
    fun `allows currentPeriodEnd for ANNUAL plan`() {
        val expiry = Instant.parse("2027-01-01T00:00:00Z")

        val subscription = Subscription(
            groupId = 1,
            plan = SubscriptionPlan.ANNUAL,
            status = SubscriptionStatus.ACTIVE,
            currentPeriodEnd = expiry,
        )

        assertEquals(expiry, subscription.currentPeriodEnd)
    }

    @Test
    fun `allows null currentPeriodEnd for ANNUAL plan`() {
        val subscription = Subscription(
            groupId = 1,
            plan = SubscriptionPlan.ANNUAL,
            status = SubscriptionStatus.ACTIVE,
            currentPeriodEnd = null,
        )

        assertNull(subscription.currentPeriodEnd)
    }

    @Test
    fun `defaults currentPeriodEnd to null for LIFETIME plan`() {
        val subscription = Subscription(
            groupId = 1,
            plan = SubscriptionPlan.LIFETIME,
            status = SubscriptionStatus.ACTIVE,
        )

        assertNull(subscription.currentPeriodEnd)
    }

    @Test
    fun `defaults currentPeriodEnd to null for GRANDFATHERED plan`() {
        val subscription = Subscription(
            groupId = 1,
            plan = SubscriptionPlan.GRANDFATHERED,
            status = SubscriptionStatus.ACTIVE,
        )

        assertNull(subscription.currentPeriodEnd)
    }

    @Test
    fun `rejects currentPeriodEnd for LIFETIME plan`() {
        assertFailsWith<IllegalArgumentException> {
            Subscription(
                groupId = 1,
                plan = SubscriptionPlan.LIFETIME,
                status = SubscriptionStatus.ACTIVE,
                currentPeriodEnd = Instant.parse("2027-01-01T00:00:00Z"),
            )
        }
    }

    @Test
    fun `rejects currentPeriodEnd for GRANDFATHERED plan`() {
        assertFailsWith<IllegalArgumentException> {
            Subscription(
                groupId = 1,
                plan = SubscriptionPlan.GRANDFATHERED,
                status = SubscriptionStatus.ACTIVE,
                currentPeriodEnd = Instant.parse("2027-01-01T00:00:00Z"),
            )
        }
    }
}
