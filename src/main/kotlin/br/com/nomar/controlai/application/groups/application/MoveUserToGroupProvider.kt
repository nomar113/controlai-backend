package br.com.nomar.controlai.application.groups.application

import br.com.nomar.controlai.application.groups.entrypoint.database.model.GroupMemberModel
import br.com.nomar.controlai.application.groups.entrypoint.database.repository.GroupMemberRepository
import br.com.nomar.controlai.domain.groups.gateway.MoveUserToGroupGateway
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class MoveUserToGroupProvider(
    private val groupMemberRepository: GroupMemberRepository,
) : MoveUserToGroupGateway {

    @Transactional
    override fun execute(userId: Long, targetGroupId: Long): Result<Unit> {
        return runCatching {
            val existing = groupMemberRepository.findByUserId(userId)
            if (existing != null) {
                groupMemberRepository.delete(existing)
                groupMemberRepository.flush()
            }
            groupMemberRepository.save(GroupMemberModel(groupId = targetGroupId, userId = userId))
            Unit
        }
    }
}
