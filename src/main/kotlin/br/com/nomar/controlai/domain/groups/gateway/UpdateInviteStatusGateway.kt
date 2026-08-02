package br.com.nomar.controlai.domain.groups.gateway

import br.com.nomar.controlai.domain.groups.entity.GroupInvite
import br.com.nomar.controlai.domain.groups.entity.InviteStatus

fun interface UpdateInviteStatusGateway {
    fun execute(inviteId: Long, status: InviteStatus): Result<GroupInvite>
}
