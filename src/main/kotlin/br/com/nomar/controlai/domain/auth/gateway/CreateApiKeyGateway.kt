package br.com.nomar.controlai.domain.auth.gateway

import br.com.nomar.controlai.domain.auth.entity.ApiKey

fun interface CreateApiKeyGateway {
    fun execute(apiKey: ApiKey): Result<ApiKey>
}
