package br.com.nomar.controlai.application.auth.application

import br.com.nomar.controlai.application.auth.entrypoint.database.repository.RefreshTokenRepository
import br.com.nomar.controlai.domain.auth.gateway.RevokeRefreshTokenGateway
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class RevokeRefreshTokenProvider(
    private val refreshTokenRepository: RefreshTokenRepository,
) : RevokeRefreshTokenGateway {

    override fun execute(tokenHash: String): Result<Unit> {
        return runCatching {
            refreshTokenRepository.findByTokenHash(tokenHash)?.let { model ->
                if (model.revokedAt == null) {
                    model.revokedAt = Instant.now()
                    refreshTokenRepository.save(model)
                }
            }
            Unit
        }
    }
}
