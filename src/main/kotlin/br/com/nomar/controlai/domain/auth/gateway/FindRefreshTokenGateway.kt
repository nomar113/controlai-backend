package br.com.nomar.controlai.domain.auth.gateway

import br.com.nomar.controlai.domain.auth.entity.RefreshToken

fun interface FindRefreshTokenGateway {
    fun execute(tokenHash: String): Result<RefreshToken?>
}
