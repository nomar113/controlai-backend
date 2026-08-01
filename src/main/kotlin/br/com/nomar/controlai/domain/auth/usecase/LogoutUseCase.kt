package br.com.nomar.controlai.domain.auth.usecase

import br.com.nomar.controlai.domain.auth.TokenHasher
import br.com.nomar.controlai.domain.auth.gateway.RevokeRefreshTokenGateway
import org.springframework.stereotype.Component

@Component
class LogoutUseCase(
    private val revokeRefreshTokenGateway: RevokeRefreshTokenGateway,
) {

    // Idempotent: revoking an unknown or already revoked token is still a successful logout
    fun execute(refreshToken: String): Result<Unit> {
        return runCatching {
            revokeRefreshTokenGateway.execute(TokenHasher.sha256(refreshToken)).getOrThrow()
        }
    }
}
