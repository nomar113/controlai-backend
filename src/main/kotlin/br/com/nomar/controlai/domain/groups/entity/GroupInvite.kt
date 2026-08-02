package br.com.nomar.controlai.domain.groups.entity

import java.time.LocalDateTime

enum class InviteStatus { PENDING, ACCEPTED, DECLINED, CANCELLED }

data class GroupInvite(
    val id: Long? = null,
    val groupId: Long,
    val inviterUserId: Long,
    val inviteeEmail: String,
    val status: InviteStatus,
    val token: String,
    val expiresAt: LocalDateTime,
    val createdAt: LocalDateTime? = null,
) {
    fun isExpired(): Boolean = LocalDateTime.now().isAfter(expiresAt)
    fun isPending(): Boolean = status == InviteStatus.PENDING && !isExpired()
}
