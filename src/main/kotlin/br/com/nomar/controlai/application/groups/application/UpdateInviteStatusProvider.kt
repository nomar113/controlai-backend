package br.com.nomar.controlai.application.groups.application

import br.com.nomar.controlai.application.groups.converter.GroupInviteConverter
import br.com.nomar.controlai.application.groups.entrypoint.database.model.GroupInviteStatusModel
import br.com.nomar.controlai.application.groups.entrypoint.database.repository.GroupInviteRepository
import br.com.nomar.controlai.domain.groups.entity.GroupInvite
import br.com.nomar.controlai.domain.groups.entity.InviteStatus
import br.com.nomar.controlai.domain.groups.gateway.UpdateInviteStatusGateway
import org.springframework.stereotype.Component

@Component
class UpdateInviteStatusProvider(
    private val groupInviteRepository: GroupInviteRepository,
    private val converter: GroupInviteConverter,
) : UpdateInviteStatusGateway {

    override fun execute(inviteId: Long, status: InviteStatus): Result<GroupInvite> {
        return runCatching {
            val model = groupInviteRepository.findById(inviteId)
                .orElseThrow { NoSuchElementException("Invite $inviteId not found") }
            val updated = groupInviteRepository.save(
                model.copy(status = GroupInviteStatusModel.valueOf(status.name)),
            )
            converter.toEntity(updated)
        }
    }
}
