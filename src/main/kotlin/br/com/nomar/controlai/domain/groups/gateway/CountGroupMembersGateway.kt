package br.com.nomar.controlai.domain.groups.gateway

fun interface CountGroupMembersGateway {
    fun execute(groupId: Long): Result<Int>
}
