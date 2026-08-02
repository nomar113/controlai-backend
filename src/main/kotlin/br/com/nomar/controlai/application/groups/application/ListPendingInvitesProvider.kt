package br.com.nomar.controlai.application.groups.application

import br.com.nomar.controlai.application.groups.converter.GroupInviteConverter
import br.com.nomar.controlai.application.groups.entrypoint.database.model.GroupInviteStatusModel
import br.com.nomar.controlai.application.groups.entrypoint.database.repository.GroupInviteRepository
import br.com.nomar.controlai.domain.groups.entity.GroupInvite
import br.com.nomar.controlai.domain.groups.gateway.ListPendingInvitesGateway
import org.springframework.stereotype.Component

@Component
class ListPendingInvitesProvider(
    private val groupInviteRepository: GroupInviteRepository,
    private val converter: GroupInviteConverter,
) : ListPendingInvitesGateway {

    override fun execute(inviteeEmail: String): Result<List<GroupInvite>> {
        return runCatching {
            groupInviteRepository
                .findByInviteeEmailAndStatus(inviteeEmail, GroupInviteStatusModel.PENDING)
                .map(converter::toEntity)
        }
    }
}
