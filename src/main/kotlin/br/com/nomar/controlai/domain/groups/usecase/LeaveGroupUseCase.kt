package br.com.nomar.controlai.domain.groups.usecase

import br.com.nomar.controlai.domain.auth.gateway.FindUserByIdGateway
import br.com.nomar.controlai.domain.groups.exception.CannotLeavePersonalGroupException
import br.com.nomar.controlai.domain.groups.gateway.CountGroupMembersGateway
import br.com.nomar.controlai.domain.groups.gateway.CreatePersonalGroupGateway
import br.com.nomar.controlai.domain.groups.gateway.FindUserGroupGateway
import br.com.nomar.controlai.domain.groups.gateway.MoveUserToGroupGateway
import org.springframework.stereotype.Component

@Component
class LeaveGroupUseCase(
    private val findUserByIdGateway: FindUserByIdGateway,
    private val findUserGroupGateway: FindUserGroupGateway,
    private val countGroupMembersGateway: CountGroupMembersGateway,
    private val createPersonalGroupGateway: CreatePersonalGroupGateway,
    private val moveUserToGroupGateway: MoveUserToGroupGateway,
) {

    // User leaves their current (shared) group and gets a new empty personal group with default categories.
    // Data remains in the original group (other participants keep it).
    // A user cannot leave a group if they are the sole member (it is already a personal group).
    fun execute(userId: Long): Result<Long> {
        return runCatching {
            val user = findUserByIdGateway.execute(userId).getOrThrow()
                ?: throw NoSuchElementException("User not found")

            val group = findUserGroupGateway.execute(userId).getOrThrow()
                ?: throw IllegalStateException("User has no group membership")

            val memberCount = countGroupMembersGateway.execute(group.id!!).getOrThrow()
            if (memberCount <= 1) throw CannotLeavePersonalGroupException()

            val newGroupId = createPersonalGroupGateway.execute(userId, user.name).getOrThrow()
            moveUserToGroupGateway.execute(userId, newGroupId).getOrThrow()
            newGroupId
        }
    }
}
