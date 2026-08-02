package br.com.nomar.controlai.domain.auth.gateway

fun interface RevokeAllRefreshTokensByUserIdGateway {
    fun execute(userId: Long): Result<Unit>
}
