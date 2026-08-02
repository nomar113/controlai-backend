package br.com.nomar.controlai.domain.auth.gateway

import br.com.nomar.controlai.domain.auth.entity.ApiKey

fun interface FindApiKeyByGroupIdGateway {
    fun execute(groupId: Long): Result<ApiKey?>
}
