package br.com.nomar.controlai.domain.groups.gateway

import br.com.nomar.controlai.domain.groups.entity.GroupInvite

fun interface CreateGroupInviteGateway {
    fun execute(invite: GroupInvite): Result<GroupInvite>
}
