package br.com.nomar.controlai.application.groups.application

import br.com.nomar.controlai.application.groups.converter.GroupInviteConverter
import br.com.nomar.controlai.application.groups.entrypoint.database.repository.GroupInviteRepository
import br.com.nomar.controlai.domain.groups.entity.GroupInvite
import br.com.nomar.controlai.domain.groups.gateway.FindInviteByIdGateway
import org.springframework.stereotype.Component

@Component
class FindInviteByIdProvider(
    private val groupInviteRepository: GroupInviteRepository,
    private val converter: GroupInviteConverter,
) : FindInviteByIdGateway {

    override fun execute(inviteId: Long): Result<GroupInvite?> {
        return runCatching {
            groupInviteRepository.findById(inviteId).orElse(null)?.let(converter::toEntity)
        }
    }
}
