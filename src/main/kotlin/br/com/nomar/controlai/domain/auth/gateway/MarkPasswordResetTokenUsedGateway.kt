package br.com.nomar.controlai.domain.auth.gateway

fun interface MarkPasswordResetTokenUsedGateway {
    fun execute(tokenId: Long): Result<Unit>
}
