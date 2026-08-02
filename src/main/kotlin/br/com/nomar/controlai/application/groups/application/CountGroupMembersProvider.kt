package br.com.nomar.controlai.application.groups.application

import br.com.nomar.controlai.application.groups.entrypoint.database.repository.GroupMemberRepository
import br.com.nomar.controlai.domain.groups.gateway.CountGroupMembersGateway
import org.springframework.stereotype.Component

@Component
class CountGroupMembersProvider(
    private val groupMemberRepository: GroupMemberRepository,
) : CountGroupMembersGateway {

    override fun execute(groupId: Long): Result<Int> {
        return runCatching {
            groupMemberRepository.countByGroupId(groupId)
        }
    }
}
