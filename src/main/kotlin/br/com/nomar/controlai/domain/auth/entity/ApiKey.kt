package br.com.nomar.controlai.domain.auth.entity

import java.time.LocalDateTime

data class ApiKey(
    val id: Long? = null,
    val groupId: Long,
    val keyHash: String,
    val label: String = DEFAULT_LABEL,
    val createdAt: LocalDateTime? = null,
    val revokedAt: LocalDateTime? = null,
) {
    fun isRevoked(): Boolean = revokedAt != null

    companion object {
        const val DEFAULT_LABEL = "iPhone Shortcut"
        const val RAW_KEY_PREFIX = "cap_"
    }
}
