package br.com.nomar.controlai.domain.groups.entity

import java.time.Instant

enum class InviteStatus { PENDING, ACCEPTED, DECLINED, CANCELLED }

data class GroupInvite(
    val id: Long? = null,
    val groupId: Long,
    val inviterUserId: Long,
    val inviteeEmail: String,
    val status: InviteStatus,
    val token: String,
    val expiresAt: Instant,
    val createdAt: Instant? = null,
) {
    fun isExpired(): Boolean = Instant.now().isAfter(expiresAt)
    fun isPending(): Boolean = status == InviteStatus.PENDING && !isExpired()
}
