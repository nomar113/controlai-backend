package br.com.nomar.controlai.domain.groups.usecase

import br.com.nomar.controlai.domain.groups.entity.InviteStatus
import br.com.nomar.controlai.domain.groups.exception.InviteNotFoundException
import br.com.nomar.controlai.domain.groups.exception.InviteNotPendingException
import br.com.nomar.controlai.domain.groups.exception.PersonalGroupHasDataException
import br.com.nomar.controlai.domain.groups.gateway.DeleteGroupDataGateway
import br.com.nomar.controlai.domain.groups.gateway.FindInviteByIdGateway
import br.com.nomar.controlai.domain.groups.gateway.FindUserGroupGateway
import br.com.nomar.controlai.domain.groups.gateway.GroupHasDataGateway
import br.com.nomar.controlai.domain.groups.gateway.MoveUserToGroupGateway
import br.com.nomar.controlai.domain.groups.gateway.UpdateInviteStatusGateway
import org.springframework.stereotype.Component

@Component
class AcceptInviteUseCase(
    private val findInviteByIdGateway: FindInviteByIdGateway,
    private val findUserGroupGateway: FindUserGroupGateway,
    private val groupHasDataGateway: GroupHasDataGateway,
    private val deleteGroupDataGateway: DeleteGroupDataGateway,
    private val moveUserToGroupGateway: MoveUserToGroupGateway,
    private val updateInviteStatusGateway: UpdateInviteStatusGateway,
) {

    // force=true is required when the accepting user's personal group already has data;
    // the data is discarded before they join the inviter's group (per product decision).
    fun execute(inviteId: Long, acceptingUserId: Long, force: Boolean = false): Result<Unit> {
        return runCatching {
            val invite = findInviteByIdGateway.execute(inviteId).getOrThrow()
                ?: throw InviteNotFoundException()

            if (!invite.isPending()) throw InviteNotPendingException()

            val currentGroup = findUserGroupGateway.execute(acceptingUserId).getOrThrow()
                ?: throw IllegalStateException("User has no group membership")

            val currentGroupId = currentGroup.id!!

            // Block if personal group has data unless caller confirmed with force=true
            val hasData = groupHasDataGateway.execute(currentGroupId).getOrThrow()
            if (hasData && !force) throw PersonalGroupHasDataException()

            if (hasData) {
                deleteGroupDataGateway.execute(currentGroupId).getOrThrow()
            }

            moveUserToGroupGateway.execute(acceptingUserId, invite.groupId).getOrThrow()
            updateInviteStatusGateway.execute(inviteId, InviteStatus.ACCEPTED).getOrThrow()
        }
    }
}
