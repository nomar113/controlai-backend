package br.com.nomar.controlai.domain.groups.gateway

import br.com.nomar.controlai.domain.groups.entity.Group

fun interface FindUserGroupGateway {
    fun execute(userId: Long): Result<Group?>
}
