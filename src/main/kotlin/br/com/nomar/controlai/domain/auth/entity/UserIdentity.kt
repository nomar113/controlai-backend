package br.com.nomar.controlai.domain.auth.entity

import java.time.Instant

enum class IdentityProvider { GOOGLE, APPLE }

class UserIdentity(
    val id: Long? = null,
    val userId: Long,
    val provider: IdentityProvider,
    val subject: String,
    val appleRefreshTokenEnc: String? = null,
    val createdAt: Instant? = null,
)
