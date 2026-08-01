package br.com.nomar.controlai.application.groups.application

import br.com.nomar.controlai.application.groups.converter.GroupConverter
import br.com.nomar.controlai.application.groups.entrypoint.database.repository.GroupMemberRepository
import br.com.nomar.controlai.application.groups.entrypoint.database.repository.GroupRepository
import br.com.nomar.controlai.domain.groups.entity.Group
import br.com.nomar.controlai.domain.groups.gateway.FindUserGroupGateway
import org.springframework.stereotype.Component

@Component
class FindUserGroupProvider(
    private val groupMemberRepository: GroupMemberRepository,
    private val groupRepository: GroupRepository,
    private val converter: GroupConverter,
) : FindUserGroupGateway {

    override fun execute(userId: Long): Result<Group?> {
        return runCatching {
            groupMemberRepository.findByUserId(userId)
                ?.let { groupRepository.findById(it.groupId).orElse(null) }
                ?.let(converter::toEntity)
        }
    }
}
