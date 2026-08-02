package br.com.nomar.controlai.domain.groups.gateway

import br.com.nomar.controlai.domain.groups.entity.GroupInvite

fun interface FindInviteByIdGateway {
    fun execute(inviteId: Long): Result<GroupInvite?>
}
