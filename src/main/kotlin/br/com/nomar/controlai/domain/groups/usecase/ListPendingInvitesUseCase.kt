package br.com.nomar.controlai.domain.groups.usecase

import br.com.nomar.controlai.domain.groups.entity.GroupInvite
import br.com.nomar.controlai.domain.groups.gateway.ListPendingInvitesGateway
import org.springframework.stereotype.Component

@Component
class ListPendingInvitesUseCase(
    private val listPendingInvitesGateway: ListPendingInvitesGateway,
) {

    fun execute(inviteeEmail: String): Result<List<GroupInvite>> {
        return listPendingInvitesGateway.execute(inviteeEmail.trim().lowercase())
            .map { invites -> invites.filter { it.isPending() } }
    }
}
