package br.com.nomar.controlai.domain.groups.usecase

import br.com.nomar.controlai.domain.auth.gateway.EmailGateway
import br.com.nomar.controlai.domain.auth.gateway.FindUserByEmailGateway
import br.com.nomar.controlai.domain.groups.entity.GroupInvite
import br.com.nomar.controlai.domain.groups.entity.InviteStatus
import br.com.nomar.controlai.domain.groups.exception.InviteeAlreadyInGroupException
import br.com.nomar.controlai.domain.groups.gateway.CreateGroupInviteGateway
import br.com.nomar.controlai.domain.groups.gateway.FindUserGroupGateway
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import java.util.UUID

@Component
class InviteToGroupUseCase(
    private val findUserByEmailGateway: FindUserByEmailGateway,
    private val findUserGroupGateway: FindUserGroupGateway,
    private val createGroupInviteGateway: CreateGroupInviteGateway,
    private val emailGateway: EmailGateway,
    @Value("\${app.web-url}") private val appWebUrl: String,
) {

    fun execute(inviterUserId: Long, inviterGroupId: Long, inviteeEmail: String): Result<GroupInvite> {
        return runCatching {
            val normalizedEmail = inviteeEmail.trim().lowercase()

            // If the invitee already has an account, check that they're not already in the same group
            val invitee = findUserByEmailGateway.execute(normalizedEmail).getOrThrow()
            if (invitee != null) {
                val inviteeGroup = findUserGroupGateway.execute(invitee.id!!).getOrNull()
                if (inviteeGroup?.id == inviterGroupId) {
                    throw InviteeAlreadyInGroupException()
                }
            }

            val token = UUID.randomUUID().toString()
            val invite = createGroupInviteGateway.execute(
                GroupInvite(
                    groupId = inviterGroupId,
                    inviterUserId = inviterUserId,
                    inviteeEmail = normalizedEmail,
                    status = InviteStatus.PENDING,
                    token = token,
                    expiresAt = Instant.now().plus(Duration.ofDays(INVITE_TTL_DAYS)),
                ),
            ).getOrThrow()

            val inviteLink = "$appWebUrl/profile?invite=$token"
            val result = emailGateway.sendGroupInvite(normalizedEmail, inviteLink)
            if (result.isFailure) {
                logger.error("Failed to send invite email to {}", normalizedEmail.take(3) + "***")
            }

            invite
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(InviteToGroupUseCase::class.java)
        private const val INVITE_TTL_DAYS = 7L
    }
}
