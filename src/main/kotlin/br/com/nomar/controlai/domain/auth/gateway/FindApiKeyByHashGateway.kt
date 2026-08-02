package br.com.nomar.controlai.domain.auth.gateway

import br.com.nomar.controlai.domain.auth.entity.ApiKey

fun interface FindApiKeyByHashGateway {
    fun execute(keyHash: String): Result<ApiKey?>
}
