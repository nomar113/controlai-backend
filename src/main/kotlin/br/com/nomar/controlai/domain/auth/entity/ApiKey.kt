package br.com.nomar.controlai.domain.auth.entity

import java.time.Instant
import java.time.LocalDateTime

data class ApiKey(
    val id: Long? = null,
    val groupId: Long,
    val keyHash: String,
    val label: String = "iPhone Shortcut",
    val createdAt: LocalDateTime? = null,
    val revokedAt: Instant? = null,
) {
    fun isRevoked(): Boolean = revokedAt != null
}
