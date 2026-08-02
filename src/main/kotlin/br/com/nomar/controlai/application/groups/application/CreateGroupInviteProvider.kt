package br.com.nomar.controlai.application.groups.application

import br.com.nomar.controlai.application.groups.converter.GroupInviteConverter
import br.com.nomar.controlai.application.groups.entrypoint.database.repository.GroupInviteRepository
import br.com.nomar.controlai.domain.groups.entity.GroupInvite
import br.com.nomar.controlai.domain.groups.gateway.CreateGroupInviteGateway
import org.springframework.stereotype.Component

@Component
class CreateGroupInviteProvider(
    private val groupInviteRepository: GroupInviteRepository,
    private val converter: GroupInviteConverter,
) : CreateGroupInviteGateway {

    override fun execute(invite: GroupInvite): Result<GroupInvite> {
        return runCatching {
            converter.toEntity(groupInviteRepository.save(converter.toModel(invite)))
        }
    }
}
