package br.com.nomar.controlai.domain.auth.entity

import java.time.Instant

// Where the app receives the one-time handoff code once the Apple login completes on the backend
enum class AppleLoginPlatform { ANDROID, WEB }

class AppleLoginFlow(
    val id: Long,
    val nonce: String,
    val state: String?,
    val platform: AppleLoginPlatform,
    val expiresAt: Instant,
    val consumedAt: Instant?,
    val userId: Long?,
    val handoffExpiresAt: Instant?,
    val handoffConsumedAt: Instant?,
) {
    fun isOpenForCallback(now: Instant): Boolean = consumedAt == null && now.isBefore(expiresAt)

    fun isHandoffUsable(now: Instant): Boolean =
        handoffConsumedAt == null && handoffExpiresAt != null && now.isBefore(handoffExpiresAt)
}
