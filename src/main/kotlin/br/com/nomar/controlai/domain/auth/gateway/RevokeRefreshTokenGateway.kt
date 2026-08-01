package br.com.nomar.controlai.domain.auth.gateway

fun interface RevokeRefreshTokenGateway {
    fun execute(tokenHash: String): Result<Unit>
}
