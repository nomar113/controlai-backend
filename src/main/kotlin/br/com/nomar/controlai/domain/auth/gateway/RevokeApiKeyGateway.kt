package br.com.nomar.controlai.domain.auth.gateway

fun interface RevokeApiKeyGateway {
    fun revokeByGroupId(groupId: Long): Result<Unit>
}
