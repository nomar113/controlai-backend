package br.com.nomar.controlai.domain.billing

import br.com.nomar.controlai.domain.auth.entity.PasswordResetToken
import br.com.nomar.controlai.domain.auth.entity.User
import br.com.nomar.controlai.domain.auth.exception.EmailAlreadyUsedException
import br.com.nomar.controlai.domain.auth.gateway.EmailGateway
import br.com.nomar.controlai.domain.billing.entity.KiwifyWebhookEvent
import br.com.nomar.controlai.domain.billing.entity.Subscription
import br.com.nomar.controlai.domain.billing.entity.SubscriptionPlan
import br.com.nomar.controlai.domain.billing.entity.SubscriptionStatus
import br.com.nomar.controlai.domain.billing.usecase.HandleKiwifyWebhookUseCase
import br.com.nomar.controlai.domain.billing.usecase.ParsedKiwifyWebhookEvent
import br.com.nomar.controlai.domain.groups.entity.Group
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HandleKiwifyWebhookUseCaseTest {

    private val annualProductId = "annual-product-id"
    private val lifetimeProductId = "lifetime-product-id"
    private val existingEmail = "ramon@controlai.test"
    private val existingGroupId = 42L
    private val appWebUrl = "http://localhost:3000"

    private var savedEvents = mutableListOf<KiwifyWebhookEvent>()
    private var upsertedSubscriptions = mutableListOf<Subscription>()
    private var existingEventIds = mutableSetOf<String>()
    private var activeSubscriptionByGroup = mutableMapOf<Long, Subscription>()

    // Onboarding-related fakes: usersByEmail/groupsByUserId start seeded with the "already
    // exists" fixture (id=1 / existingGroupId, already onboarded with a password) and grow as
    // CreateUserWithPersonalGroupGateway is exercised, mirroring the real gateway's behaviour of
    // creating both atomically.
    private var usersByEmail = mutableMapOf(
        existingEmail to User(id = 1, name = "Ramon", email = existingEmail, passwordHash = "already-set-hash"),
    )
    private var groupsByUserId = mutableMapOf(1L to Group(id = existingGroupId, name = "Ramon"))
    private var nextUserId = 2L
    private var nextGroupId = existingGroupId + 1
    private var createdPasswordResetTokens = mutableListOf<PasswordResetToken>()
    private var welcomeEmailsSent = mutableListOf<Pair<String, String>>()

    // When set, the next createUserWithPersonalGroupGateway call fails with
    // EmailAlreadyUsedException instead of creating the user, simulating a concurrent webhook
    // for the same new email winning the race and creating the account first.
    private var simulateConcurrentAccountCreationFor: String? = null

    private fun buildUseCase() = HandleKiwifyWebhookUseCase(
        findKiwifyWebhookEventByIdGateway = { id ->
            Result.success(if (existingEventIds.contains(id)) KiwifyWebhookEvent(kiwifyEventId = id, orderStatus = "x", rawPayload = "{}") else null)
        },
        saveKiwifyWebhookEventGateway = { event ->
            savedEvents.add(event)
            existingEventIds.add(event.kiwifyEventId)
            Result.success(event)
        },
        findActiveSubscriptionByGroupIdGateway = { groupId -> Result.success(activeSubscriptionByGroup[groupId]) },
        upsertSubscriptionGateway = { subscription ->
            upsertedSubscriptions.add(subscription)
            if (subscription.status == SubscriptionStatus.ACTIVE) {
                activeSubscriptionByGroup[subscription.groupId] = subscription
            } else {
                activeSubscriptionByGroup.remove(subscription.groupId)
            }
            Result.success(subscription)
        },
        findUserByEmailGateway = { email -> Result.success(usersByEmail[email]) },
        findUserGroupGateway = { userId -> Result.success(groupsByUserId[userId]) },
        createUserWithPersonalGroupGateway = { user ->
            if (simulateConcurrentAccountCreationFor == user.email) {
                simulateConcurrentAccountCreationFor = null
                // A "concurrent" request wins the race first: the account exists by the time this
                // one looks it up again, mirroring CreateUserWithPersonalGroupProvider's real
                // unique-constraint safety net.
                usersByEmail[user.email] = User(id = nextUserId, name = user.name, email = user.email)
                groupsByUserId[nextUserId] = Group(id = nextGroupId, name = user.name)
                nextUserId += 1
                nextGroupId += 1
                Result.failure(EmailAlreadyUsedException())
            } else {
                val createdUser = User(id = nextUserId, name = user.name, email = user.email)
                usersByEmail[user.email] = createdUser
                groupsByUserId[nextUserId] = Group(id = nextGroupId, name = user.name)
                nextUserId += 1
                nextGroupId += 1
                Result.success(createdUser)
            }
        },
        createPasswordResetTokenGateway = { token ->
            createdPasswordResetTokens.add(token)
            Result.success(token)
        },
        emailGateway = object : EmailGateway {
            override fun sendPasswordReset(toEmail: String, toName: String, resetLink: String) = Result.success(Unit)
            override fun sendGroupInvite(toEmail: String, inviteLink: String) = Result.success(Unit)
            override fun sendWelcomeSetPassword(toEmail: String, toName: String, setPasswordLink: String): Result<Unit> {
                welcomeEmailsSent.add(toEmail to setPasswordLink)
                return Result.success(Unit)
            }
        },
        meterRegistry = SimpleMeterRegistry(),
        annualProductId = annualProductId,
        lifetimeProductId = lifetimeProductId,
        appWebUrl = appWebUrl,
    )

    private fun event(
        eventId: String,
        orderStatus: String,
        customerEmail: String?,
        customerName: String? = null,
        productId: String? = null,
        rawPayload: String = "{}",
    ) = ParsedKiwifyWebhookEvent(
        rawPayload = rawPayload,
        eventId = eventId,
        orderStatus = orderStatus,
        customerEmail = customerEmail,
        customerName = customerName,
        productId = productId,
    )

    @Test
    fun `compra_aprovada with annual product activates ANNUAL subscription`() {
        val useCase = buildUseCase()

        val result = useCase.execute(
            event(eventId = "evt-1", orderStatus = "compra_aprovada", customerEmail = existingEmail, productId = annualProductId),
        )

        assertTrue(result.isSuccess)
        assertEquals(1, upsertedSubscriptions.size)
        assertEquals(SubscriptionPlan.ANNUAL, upsertedSubscriptions[0].plan)
        assertEquals(SubscriptionStatus.ACTIVE, upsertedSubscriptions[0].status)
        assertEquals(existingGroupId, upsertedSubscriptions[0].groupId)
    }

    @Test
    fun `compra_aprovada with lifetime product activates LIFETIME subscription`() {
        val useCase = buildUseCase()

        val result = useCase.execute(
            event(eventId = "evt-2", orderStatus = "compra_aprovada", customerEmail = existingEmail, productId = lifetimeProductId),
        )

        assertTrue(result.isSuccess)
        assertEquals(SubscriptionPlan.LIFETIME, upsertedSubscriptions[0].plan)
    }

    @Test
    fun `compra_aprovada with unknown product id defaults to ANNUAL`() {
        val useCase = buildUseCase()

        val result = useCase.execute(
            event(eventId = "evt-2b", orderStatus = "compra_aprovada", customerEmail = existingEmail, productId = "some-other-product-id"),
        )

        assertTrue(result.isSuccess)
        assertEquals(SubscriptionPlan.ANNUAL, upsertedSubscriptions[0].plan)
    }

    @Test
    fun `compra_aprovada for new email creates user, group, subscription and sends set-password email`() {
        val useCase = buildUseCase()
        val newEmail = "novo@controlai.test"

        val result = useCase.execute(
            event(eventId = "evt-3", orderStatus = "compra_aprovada", customerEmail = newEmail, customerName = "Novo Cliente", productId = annualProductId),
        )

        assertTrue(result.isSuccess)
        assertEquals(1, savedEvents.size)

        val createdUser = usersByEmail[newEmail] ?: error("user was not created")
        assertEquals("Novo Cliente", createdUser.name)

        assertEquals(1, upsertedSubscriptions.size)
        assertEquals(SubscriptionPlan.ANNUAL, upsertedSubscriptions[0].plan)
        assertEquals(SubscriptionStatus.ACTIVE, upsertedSubscriptions[0].status)
        assertEquals(groupsByUserId[createdUser.id]?.id, upsertedSubscriptions[0].groupId)

        assertEquals(1, createdPasswordResetTokens.size)
        assertEquals(createdUser.id, createdPasswordResetTokens[0].userId)

        assertEquals(1, welcomeEmailsSent.size)
        assertEquals(newEmail, welcomeEmailsSent[0].first)
        assertTrue(welcomeEmailsSent[0].second.startsWith("$appWebUrl/reset-password?token="))
    }

    @Test
    fun `compra_aprovada for new email without a name falls back to a generic customer name`() {
        val useCase = buildUseCase()
        val newEmail = "sem-nome@controlai.test"

        val result = useCase.execute(
            event(eventId = "evt-3b", orderStatus = "compra_aprovada", customerEmail = newEmail, customerName = null, productId = annualProductId),
        )

        assertTrue(result.isSuccess)
        assertEquals("Cliente ControlAI", usersByEmail[newEmail]?.name)
    }

    @Test
    fun `compra_aprovada for an already existing email only updates the subscription, without creating a duplicate user`() {
        val useCase = buildUseCase()

        val result = useCase.execute(
            event(eventId = "evt-3c", orderStatus = "compra_aprovada", customerEmail = existingEmail, customerName = "Ramon Repetido", productId = annualProductId),
        )

        assertTrue(result.isSuccess)
        assertEquals(1L, usersByEmail[existingEmail]?.id)
        assertEquals(1, upsertedSubscriptions.size)
        assertEquals(existingGroupId, upsertedSubscriptions[0].groupId)
        assertTrue(welcomeEmailsSent.isEmpty())
        assertTrue(createdPasswordResetTokens.isEmpty())
    }

    @Test
    fun `compra_aprovada that loses a concurrent account-creation race still activates the subscription and sends the email`() {
        val useCase = buildUseCase()
        val newEmail = "corrida@controlai.test"
        simulateConcurrentAccountCreationFor = newEmail

        val result = useCase.execute(
            event(eventId = "evt-race", orderStatus = "compra_aprovada", customerEmail = newEmail, customerName = "Corrida", productId = lifetimeProductId),
        )

        assertTrue(result.isSuccess)
        val winnerUser = usersByEmail[newEmail] ?: error("the concurrent request should have created the user")

        assertEquals(1, upsertedSubscriptions.size)
        assertEquals(SubscriptionPlan.LIFETIME, upsertedSubscriptions[0].plan)
        assertEquals(groupsByUserId[winnerUser.id]?.id, upsertedSubscriptions[0].groupId)

        assertEquals(1, welcomeEmailsSent.size)
        assertEquals(newEmail, welcomeEmailsSent[0].first)
    }

    @Test
    fun `compra_aprovada for an existing user who never set a password resends the set-password email`() {
        usersByEmail[existingEmail] = User(id = 1, name = "Ramon", email = existingEmail, passwordHash = null)
        val useCase = buildUseCase()

        val result = useCase.execute(
            event(eventId = "evt-resend", orderStatus = "compra_aprovada", customerEmail = existingEmail, productId = annualProductId),
        )

        assertTrue(result.isSuccess)
        assertEquals(1, welcomeEmailsSent.size)
        assertEquals(existingEmail, welcomeEmailsSent[0].first)
    }

    @Test
    fun `cancellation event for an unknown email does not create an account`() {
        val useCase = buildUseCase()
        val newEmail = "cancelamento-desconhecido@controlai.test"

        val result = useCase.execute(event(eventId = "evt-3d", orderStatus = "subscription_canceled", customerEmail = newEmail))

        assertTrue(result.isSuccess)
        assertNull(usersByEmail[newEmail])
        assertTrue(upsertedSubscriptions.isEmpty())
    }

    @Test
    fun `same kiwify_event_id processed twice does not duplicate effect`() {
        val useCase = buildUseCase()

        useCase.execute(event(eventId = "evt-4", orderStatus = "compra_aprovada", customerEmail = existingEmail, productId = annualProductId))
        val secondResult = useCase.execute(
            event(eventId = "evt-4", orderStatus = "compra_aprovada", customerEmail = existingEmail, productId = annualProductId),
        )

        assertTrue(secondResult.isSuccess)
        assertEquals(1, savedEvents.size)
        assertEquals(1, upsertedSubscriptions.size)
    }

    @Test
    fun `subscription_renewed keeps subscription ACTIVE preserving plan`() {
        activeSubscriptionByGroup[existingGroupId] =
            Subscription(id = 1, groupId = existingGroupId, plan = SubscriptionPlan.LIFETIME, status = SubscriptionStatus.ACTIVE)
        val useCase = buildUseCase()

        val result = useCase.execute(event(eventId = "evt-5", orderStatus = "subscription_renewed", customerEmail = existingEmail))

        assertTrue(result.isSuccess)
        assertEquals(SubscriptionStatus.ACTIVE, upsertedSubscriptions[0].status)
        assertEquals(SubscriptionPlan.LIFETIME, upsertedSubscriptions[0].plan)
    }

    @Test
    fun `subscription_late keeps subscription ACTIVE (grace period)`() {
        activeSubscriptionByGroup[existingGroupId] =
            Subscription(id = 1, groupId = existingGroupId, plan = SubscriptionPlan.ANNUAL, status = SubscriptionStatus.ACTIVE)
        val useCase = buildUseCase()

        val result = useCase.execute(event(eventId = "evt-6", orderStatus = "subscription_late", customerEmail = existingEmail))

        assertTrue(result.isSuccess)
        assertEquals(SubscriptionStatus.ACTIVE, upsertedSubscriptions[0].status)
    }

    @Test
    fun `subscription_canceled cancels an active subscription`() {
        activeSubscriptionByGroup[existingGroupId] =
            Subscription(id = 1, groupId = existingGroupId, plan = SubscriptionPlan.ANNUAL, status = SubscriptionStatus.ACTIVE)
        val useCase = buildUseCase()

        val result = useCase.execute(event(eventId = "evt-7", orderStatus = "subscription_canceled", customerEmail = existingEmail))

        assertTrue(result.isSuccess)
        assertEquals(SubscriptionStatus.CANCELLED, upsertedSubscriptions[0].status)
    }

    @Test
    fun `compra_reembolsada cancels an active subscription`() {
        activeSubscriptionByGroup[existingGroupId] =
            Subscription(id = 1, groupId = existingGroupId, plan = SubscriptionPlan.ANNUAL, status = SubscriptionStatus.ACTIVE)
        val useCase = buildUseCase()

        val result = useCase.execute(event(eventId = "evt-8", orderStatus = "compra_reembolsada", customerEmail = existingEmail))

        assertTrue(result.isSuccess)
        assertEquals(SubscriptionStatus.CANCELLED, upsertedSubscriptions[0].status)
    }

    @Test
    fun `chargeback cancels an active subscription`() {
        activeSubscriptionByGroup[existingGroupId] =
            Subscription(id = 1, groupId = existingGroupId, plan = SubscriptionPlan.ANNUAL, status = SubscriptionStatus.ACTIVE)
        val useCase = buildUseCase()

        val result = useCase.execute(event(eventId = "evt-9", orderStatus = "chargeback", customerEmail = existingEmail))

        assertTrue(result.isSuccess)
        assertEquals(SubscriptionStatus.CANCELLED, upsertedSubscriptions[0].status)
    }

    @Test
    fun `cancellation event with no active subscription is a no-op`() {
        val useCase = buildUseCase()

        val result = useCase.execute(event(eventId = "evt-10", orderStatus = "subscription_canceled", customerEmail = existingEmail))

        assertTrue(result.isSuccess)
        assertTrue(upsertedSubscriptions.isEmpty())
    }

    @Test
    fun `unmapped order_status is ignored without error`() {
        val useCase = buildUseCase()

        val result = useCase.execute(event(eventId = "evt-11", orderStatus = "compra_recusada", customerEmail = existingEmail))

        assertTrue(result.isSuccess)
        assertEquals(1, savedEvents.size)
        assertTrue(upsertedSubscriptions.isEmpty())
    }

    @Test
    fun `blank customer email is a no-op besides recording the event`() {
        val useCase = buildUseCase()

        val result = useCase.execute(
            event(eventId = "evt-12", orderStatus = "compra_aprovada", customerEmail = null, productId = annualProductId),
        )

        assertTrue(result.isSuccess)
        assertEquals(1, savedEvents.size)
        assertTrue(upsertedSubscriptions.isEmpty())
        assertNull(activeSubscriptionByGroup[existingGroupId])
    }
}
