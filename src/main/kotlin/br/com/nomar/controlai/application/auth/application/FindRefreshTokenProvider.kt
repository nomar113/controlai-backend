package br.com.nomar.controlai.application.auth.application

import br.com.nomar.controlai.application.auth.converter.RefreshTokenConverter
import br.com.nomar.controlai.application.auth.entrypoint.database.repository.RefreshTokenRepository
import br.com.nomar.controlai.domain.auth.entity.RefreshToken
import br.com.nomar.controlai.domain.auth.gateway.FindRefreshTokenGateway
import org.springframework.stereotype.Component

@Component
class FindRefreshTokenProvider(
    private val refreshTokenRepository: RefreshTokenRepository,
    private val converter: RefreshTokenConverter,
) : FindRefreshTokenGateway {

    override fun execute(tokenHash: String): Result<RefreshToken?> {
        return runCatching {
            refreshTokenRepository.findByTokenHash(tokenHash)?.let(converter::toEntity)
        }
    }
}
