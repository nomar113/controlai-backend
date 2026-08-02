package br.com.nomar.controlai.application.auth.application

import br.com.nomar.controlai.application.auth.entrypoint.database.repository.RefreshTokenRepository
import br.com.nomar.controlai.domain.auth.gateway.RevokeAllRefreshTokensByUserIdGateway
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class RevokeAllRefreshTokensByUserIdProvider(
    private val refreshTokenRepository: RefreshTokenRepository,
) : RevokeAllRefreshTokensByUserIdGateway {

    override fun execute(userId: Long): Result<Unit> {
        return runCatching {
            refreshTokenRepository.revokeAllActiveByUserId(userId, Instant.now())
        }
    }
}
