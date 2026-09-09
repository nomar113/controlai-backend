package br.com.nomar.controlai.application.billing

import br.com.nomar.controlai.application.billing.application.FindActiveSubscriptionByGroupIdProvider
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
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@SpringBootTest
class FindActiveSubscriptionByGroupIdProviderTest {

    @Autowired
    private lateinit var provider: FindActiveSubscriptionByGroupIdProvider

    @Autowired
    private lateinit var upsertProvider: UpsertSubscriptionProvider

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
        val group = groupRepository.save(GroupModel(name = "Grupo Teste Billing Find"))
        createdGroupIds.add(group.id!!)
        return group.id!!
    }

    @Test
    fun `should return null when group has no subscription`() {
        val groupId = createGroup()

        val result = provider.execute(groupId)

        assertTrue(result.isSuccess)
        assertNull(result.getOrThrow())
    }

    @Test
    fun `should return the subscription after multiple status updates over time when currently active`() {
        val groupId = createGroup()

        upsertProvider.execute(
            Subscription(groupId = groupId, plan = SubscriptionPlan.ANNUAL, status = SubscriptionStatus.ACTIVE)
        )
        upsertProvider.execute(
            Subscription(groupId = groupId, plan = SubscriptionPlan.ANNUAL, status = SubscriptionStatus.CANCELLED)
        )

        val whileCancelled = provider.execute(groupId)
        assertTrue(whileCancelled.isSuccess)
        assertNull(whileCancelled.getOrThrow())

        upsertProvider.execute(
            Subscription(groupId = groupId, plan = SubscriptionPlan.ANNUAL, status = SubscriptionStatus.ACTIVE)
        )

        val whileActive = provider.execute(groupId)
        assertTrue(whileActive.isSuccess)
        val subscription = whileActive.getOrThrow()
        assertEquals(groupId, subscription?.groupId)
        assertEquals(SubscriptionStatus.ACTIVE, subscription?.status)
    }
}
