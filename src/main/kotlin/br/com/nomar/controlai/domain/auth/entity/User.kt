package br.com.nomar.controlai.domain.auth.entity

import java.time.Instant

class User(
    val id: Long? = null,
    val name: String,
    val email: String,
    val passwordHash: String? = null,
    val googleSub: String? = null,
    val deletionRequestedAt: Instant? = null,
    val deletionScheduledFor: Instant? = null,
)
