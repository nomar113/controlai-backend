package br.com.nomar.controlai.domain.groups.gateway

import br.com.nomar.controlai.domain.groups.entity.GroupInvite

fun interface ListPendingInvitesGateway {
    fun execute(inviteeEmail: String): Result<List<GroupInvite>>
}
