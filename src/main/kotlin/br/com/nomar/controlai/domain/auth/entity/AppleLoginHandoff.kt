package br.com.nomar.controlai.domain.auth.entity

import java.time.Instant

data class AppleLoginHandoff(
    val flowId: Long,
    val now: Instant,
    val codeHash: String,
    val userId: Long,
    val expiresAt: Instant,
)
