package br.com.nomar.controlai.application.billing

import br.com.nomar.controlai.application.billing.application.UpsertSubscriptionProvider
import br.com.nomar.controlai.application.groups.entrypoint.database.model.GroupModel
import br.com.nomar.controlai.application.groups.entrypoint.database.repository.GroupRepository
import br.com.nomar.controlai.domain.billing.entity.Subscription
import br.com.nomar.controlai.domain.billing.entity.SubscriptionPlan
import br.com.nomar.controlai.domain.billing.entity.SubscriptionStatus
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@SpringBootTest
class UpsertSubscriptionProviderTest {

    @Autowired
    private lateinit var provider: UpsertSubscriptionProvider

    @Autowired
    private lateinit var groupRepository: GroupRepository

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    private val createdGroupIds = mutableListOf<Long>()

    @AfterEach
    fun cleanup() {
        createdGroupIds.forEach { groupId ->
            jdbcTemplate.update("DELETE FROM subscriptions WHERE group_id = ?", groupId)
            jdbcTemplate.update("DELETE FROM `groups` WHERE id = ?", groupId)
        }
        createdGroupIds.clear()
    }

    private fun createGroup(): Long {
        val group = groupRepository.save(GroupModel(name = "Grupo Teste Billing Upsert"))
        createdGroupIds.add(group.id!!)
        return group.id!!
    }

    private fun countSubscriptionsForGroup(groupId: Long): Int =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM subscriptions WHERE group_id = ?", Int::class.java, groupId
        ) ?: 0

    @Test
    fun `should create a new subscription when the group has none`() {
        val groupId = createGroup()

        val result = provider.execute(
            Subscription(groupId = groupId, plan = SubscriptionPlan.ANNUAL, status = SubscriptionStatus.ACTIVE)
        )

        assertTrue(result.isSuccess)
        val saved = result.getOrThrow()
        assertNotNull(saved.id)
        assertEquals(groupId, saved.groupId)
        assertEquals(1, countSubscriptionsForGroup(groupId))
    }

    @Test
    fun `should update the existing subscription instead of creating a new row`() {
        val groupId = createGroup()
        // TIMESTAMP columns in this schema have no fractional-second precision.
        val periodEnd = Instant.now().plusSeconds(3600).truncatedTo(ChronoUnit.SECONDS)

        val created = provider.execute(
            Subscription(groupId = groupId, plan = SubscriptionPlan.ANNUAL, status = SubscriptionStatus.ACTIVE)
        ).getOrThrow()

        val updated = provider.execute(
            Subscription(
                groupId = groupId,
                plan = SubscriptionPlan.ANNUAL,
                status = SubscriptionStatus.CANCELLED,
                kiwifyOrderId = "order-789",
                currentPeriodEnd = periodEnd,
            )
        ).getOrThrow()

        assertEquals(created.id, updated.id)
        assertEquals(SubscriptionStatus.CANCELLED, updated.status)
        assertEquals("order-789", updated.kiwifyOrderId)
        assertEquals(periodEnd, updated.currentPeriodEnd)
        assertEquals(1, countSubscriptionsForGroup(groupId))
    }
}
