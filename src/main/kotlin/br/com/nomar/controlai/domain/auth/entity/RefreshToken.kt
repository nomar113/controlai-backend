package br.com.nomar.controlai.domain.auth.entity

import java.time.Instant

class RefreshToken(
    val id: Long? = null,
    val userId: Long,
    val tokenHash: String,
    val expiresAt: Instant,
    val absoluteExpiresAt: Instant,
    val revokedAt: Instant? = null,
    val replacedById: Long? = null,
) {

    // A token that was already rotated or revoked must never be accepted again
    fun isRotatedOrRevoked(): Boolean = revokedAt != null || replacedById != null

    fun isExpired(now: Instant): Boolean = expiresAt.isBefore(now) || absoluteExpiresAt.isBefore(now)
}
