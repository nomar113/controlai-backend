package br.com.nomar.controlai.application.billing

import br.com.nomar.controlai.application.billing.converter.SubscriptionConverter
import br.com.nomar.controlai.application.billing.entrypoint.database.model.SubscriptionModel
import br.com.nomar.controlai.application.billing.entrypoint.database.model.SubscriptionPlanModel
import br.com.nomar.controlai.application.billing.entrypoint.database.model.SubscriptionStatusModel
import br.com.nomar.controlai.domain.billing.entity.Subscription
import br.com.nomar.controlai.domain.billing.entity.SubscriptionPlan
import br.com.nomar.controlai.domain.billing.entity.SubscriptionStatus
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SubscriptionConverterTest {

    private val converter = SubscriptionConverter()

    @Test
    fun `should convert model to domain entity`() {
        val periodEnd = Instant.now()
        val model = SubscriptionModel(
            id = 1L,
            groupId = 10L,
            plan = SubscriptionPlanModel.ANNUAL,
            status = SubscriptionStatusModel.ACTIVE,
            kiwifyOrderId = "order-123",
            currentPeriodEnd = periodEnd,
        )

        val entity = converter.toEntity(model)

        assertEquals(1L, entity.id)
        assertEquals(10L, entity.groupId)
        assertEquals(SubscriptionPlan.ANNUAL, entity.plan)
        assertEquals(SubscriptionStatus.ACTIVE, entity.status)
        assertEquals("order-123", entity.kiwifyOrderId)
        assertEquals(periodEnd, entity.currentPeriodEnd)
    }

    @Test
    fun `should convert domain entity to model`() {
        val entity = Subscription(
            id = 2L,
            groupId = 20L,
            plan = SubscriptionPlan.LIFETIME,
            status = SubscriptionStatus.CANCELLED,
            kiwifyOrderId = "order-456",
            currentPeriodEnd = null,
        )

        val model = converter.toModel(entity)

        assertEquals(2L, model.id)
        assertEquals(20L, model.groupId)
        assertEquals(SubscriptionPlanModel.LIFETIME, model.plan)
        assertEquals(SubscriptionStatusModel.CANCELLED, model.status)
        assertEquals("order-456", model.kiwifyOrderId)
        assertNull(model.currentPeriodEnd)
    }
}
