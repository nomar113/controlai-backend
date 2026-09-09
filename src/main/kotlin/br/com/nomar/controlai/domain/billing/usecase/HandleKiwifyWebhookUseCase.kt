package br.com.nomar.controlai.domain.billing.usecase

import br.com.nomar.controlai.domain.auth.TokenHasher
import br.com.nomar.controlai.domain.auth.entity.PasswordResetToken
import br.com.nomar.controlai.domain.auth.entity.User
import br.com.nomar.controlai.domain.auth.exception.EmailAlreadyUsedException
import br.com.nomar.controlai.domain.auth.gateway.CreatePasswordResetTokenGateway
import br.com.nomar.controlai.domain.auth.gateway.CreateUserWithPersonalGroupGateway
import br.com.nomar.controlai.domain.auth.gateway.EmailGateway
import br.com.nomar.controlai.domain.auth.gateway.FindUserByEmailGateway
import br.com.nomar.controlai.domain.billing.entity.KiwifyWebhookEvent
import br.com.nomar.controlai.domain.billing.entity.Subscription
import br.com.nomar.controlai.domain.billing.entity.SubscriptionPlan
import br.com.nomar.controlai.domain.billing.entity.SubscriptionStatus
import br.com.nomar.controlai.domain.billing.gateway.FindActiveSubscriptionByGroupIdGateway
import br.com.nomar.controlai.domain.billing.gateway.FindKiwifyWebhookEventByIdGateway
import br.com.nomar.controlai.domain.billing.gateway.SaveKiwifyWebhookEventGateway
import br.com.nomar.controlai.domain.billing.gateway.UpsertSubscriptionGateway
import br.com.nomar.controlai.domain.groups.gateway.FindUserGroupGateway
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

data class ParsedKiwifyWebhookEvent(
    val rawPayload: String,
    val eventId: String,
    val orderStatus: String,
    val customerEmail: String?,
    val customerName: String?,
    val productId: String?,
)

@Component
class HandleKiwifyWebhookUseCase(
    private val findKiwifyWebhookEventByIdGateway: FindKiwifyWebhookEventByIdGateway,
    private val saveKiwifyWebhookEventGateway: SaveKiwifyWebhookEventGateway,
    private val findActiveSubscriptionByGroupIdGateway: FindActiveSubscriptionByGroupIdGateway,
    private val upsertSubscriptionGateway: UpsertSubscriptionGateway,
    private val findUserByEmailGateway: FindUserByEmailGateway,
    private val findUserGroupGateway: FindUserGroupGateway,
    private val createUserWithPersonalGroupGateway: CreateUserWithPersonalGroupGateway,
    private val createPasswordResetTokenGateway: CreatePasswordResetTokenGateway,
    private val emailGateway: EmailGateway,
    private val meterRegistry: MeterRegistry,
    @Value("\${kiwify.product.annual-id}") private val annualProductId: String,
    @Value("\${kiwify.product.lifetime-id}") private val lifetimeProductId: String,
    @Value("\${app.web-url}") private val appWebUrl: String,
) {

    fun execute(event: ParsedKiwifyWebhookEvent): Result<Unit> {
        return runCatching {
            if (findKiwifyWebhookEventByIdGateway.execute(event.eventId).getOrThrow() != null) {
                log.info("Ignoring duplicate Kiwify webhook event {}", event.eventId)
                recordMetric(event.orderStatus, "duplicate")
                return@runCatching
            }

            saveKiwifyWebhookEventGateway.execute(
                KiwifyWebhookEvent(
                    kiwifyEventId = event.eventId,
                    orderStatus = event.orderStatus,
                    rawPayload = event.rawPayload,
                    processedAt = Instant.now(),
                ),
            ).getOrThrow()

            val email = event.customerEmail?.trim()?.lowercase()
            if (email.isNullOrBlank()) {
                log.warn("Kiwify webhook {} has no customer email; nothing to update", event.eventId)
                recordMetric(event.orderStatus, "success")
                return@runCatching
            }

            val existingUser = findUserByEmailGateway.execute(email).getOrThrow()
            if (existingUser == null) {
                // Email not found: only a compra_aprovada creates a brand-new account. Any other
                // order_status referencing an unknown email is not actionable (e.g. a cancellation
                // for an account that was never created) and is simply ignored.
                if (event.orderStatus == "compra_aprovada") {
                    onboardNewCustomer(email, event.customerName, event.productId)
                } else {
                    log.info(
                        "Kiwify webhook {}: no existing account for {}, ignoring order_status '{}'",
                        event.eventId,
                        maskEmail(email),
                        event.orderStatus,
                    )
                }
                recordMetric(event.orderStatus, "success")
                return@runCatching
            }

            val groupId = findUserGroupGateway.execute(existingUser.id!!).getOrThrow()?.id
            if (groupId == null) {
                log.warn("Kiwify webhook {}: user {} has no personal group; skipping", event.eventId, maskEmail(email))
                recordMetric(event.orderStatus, "success")
                return@runCatching
            }

            applyOrderStatus(existingUser, groupId, event.orderStatus, event.productId)
            recordMetric(event.orderStatus, "success")
        }.onFailure { recordMetric(event.orderStatus, "error") }
    }

    private fun applyOrderStatus(user: User, groupId: Long, orderStatus: String, productId: String?) {
        when (orderStatus) {
            "compra_aprovada" -> {
                activateSubscription(groupId, user.email, productId)
                // Covers both a customer buying again before ever setting a password, and the
                // race-condition fallback below, where onboarding's own e-mail step never ran.
                if (user.passwordHash == null) sendWelcomeSetPasswordEmail(user)
            }
            "subscription_renewed" -> transitionActiveSubscription(groupId, user.email, SubscriptionStatus.ACTIVE, "renewed")
            // Kiwify retries a failed charge before giving up on the subscription: keep access
            // active on the first "late" notice and only revoke on an explicit cancellation/
            // refund/chargeback event, to avoid cutting access during the retry grace period.
            "subscription_late" -> transitionActiveSubscription(groupId, user.email, SubscriptionStatus.ACTIVE, "kept active (grace period)")
            "subscription_canceled", "compra_reembolsada", "chargeback" ->
                transitionActiveSubscription(groupId, user.email, SubscriptionStatus.CANCELLED, "cancelled")
            else -> log.info("Ignoring Kiwify order_status '{}' for group {}", orderStatus, groupId)
        }
    }

    private fun activateSubscription(groupId: Long, email: String, productId: String?) {
        val plan = when (productId) {
            lifetimeProductId -> SubscriptionPlan.LIFETIME
            else -> {
                if (productId != null && productId != annualProductId) {
                    log.warn("Unknown Kiwify product id '{}' for group {}; defaulting to ANNUAL", productId, groupId)
                }
                SubscriptionPlan.ANNUAL
            }
        }

        upsertSubscriptionGateway.execute(
            Subscription(groupId = groupId, plan = plan, status = SubscriptionStatus.ACTIVE),
        ).getOrThrow()
        log.info("Subscription activated for group {} (plan={}, email={})", groupId, plan, maskEmail(email))
    }

    private fun transitionActiveSubscription(groupId: Long, email: String, newStatus: SubscriptionStatus, action: String) {
        val current = findActiveSubscriptionByGroupIdGateway.execute(groupId).getOrThrow() ?: return
        upsertSubscriptionGateway.execute(current.copy(status = newStatus)).getOrThrow()
        log.info("Subscription {} for group {} (email={})", action, groupId, maskEmail(email))
    }

    // Creates the buyer's account (reusing the same gateway RegisterUserUseCase relies on) and
    // applies this purchase to the freshly created personal group. If a concurrent webhook for
    // the same new email (e.g. the annual purchase and the lifetime order bump arriving together)
    // wins the race and creates the account first, the gateway's unique-constraint safety net
    // raises EmailAlreadyUsedException here instead of silently dropping this purchase's effect:
    // fall back to applying it against the account the other request just created.
    private fun onboardNewCustomer(email: String, customerName: String?, productId: String?) {
        val name = customerName?.trim()?.takeIf { it.isNotBlank() } ?: DEFAULT_CUSTOMER_NAME
        val createdUser = createUserWithPersonalGroupGateway.execute(User(name = name, email = email))
            .getOrElse { error ->
                if (error !is EmailAlreadyUsedException) throw error
                return applyToRaceWinnerAccount(email, productId)
            }
        val groupId = findUserGroupGateway.execute(createdUser.id!!).getOrThrow()?.id
            ?: error("Personal group not found right after creation for user ${createdUser.id}")
        applyOrderStatus(createdUser, groupId, "compra_aprovada", productId)
    }

    private fun applyToRaceWinnerAccount(email: String, productId: String?) {
        val user = findUserByEmailGateway.execute(email).getOrThrow()
            ?: error("User not found right after EmailAlreadyUsedException for ${maskEmail(email)}")
        val groupId = findUserGroupGateway.execute(user.id!!).getOrThrow()?.id ?: return
        applyOrderStatus(user, groupId, "compra_aprovada", productId)
    }

    // Reuses the "forgot password" token mechanism instead of a separate flow.
    private fun sendWelcomeSetPasswordEmail(user: User) {
        val rawToken = UUID.randomUUID().toString()
        val token = PasswordResetToken(
            userId = user.id!!,
            tokenHash = TokenHasher.sha256(rawToken),
            expiresAt = Instant.now().plus(WELCOME_TOKEN_TTL_HOURS, ChronoUnit.HOURS),
        )
        createPasswordResetTokenGateway.execute(token).getOrThrow()
        val setPasswordLink = "$appWebUrl/reset-password?token=$rawToken"
        emailGateway.sendWelcomeSetPassword(user.email, user.name, setPasswordLink)
            .onFailure { log.error("Failed to send welcome/set-password email for user {}", user.id, it) }
    }

    private fun recordMetric(orderStatus: String, result: String) {
        meterRegistry.counter("kiwify_webhook_events_total", "order_status", orderStatus, "result", result).increment()
    }

    private fun maskEmail(email: String) = email.take(3) + "***"

    companion object {
        private val log = LoggerFactory.getLogger(HandleKiwifyWebhookUseCase::class.java)
        private const val WELCOME_TOKEN_TTL_HOURS = 72L
        private const val DEFAULT_CUSTOMER_NAME = "Cliente ControlAI"
    }
}
