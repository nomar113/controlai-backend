package br.com.nomar.controlai.domain.auth.usecase

import br.com.nomar.controlai.domain.auth.TokenHasher
import br.com.nomar.controlai.domain.auth.entity.AuthSession
import br.com.nomar.controlai.domain.auth.exception.InvalidRefreshTokenException
import br.com.nomar.controlai.domain.auth.gateway.FindRefreshTokenGateway
import br.com.nomar.controlai.domain.auth.gateway.FindUserByIdGateway
import br.com.nomar.controlai.domain.auth.gateway.RevokeTokenFamilyGateway
import br.com.nomar.controlai.domain.auth.gateway.RotateRefreshTokenGateway
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class RefreshSessionUseCase(
    private val findRefreshTokenGateway: FindRefreshTokenGateway,
    private val findUserByIdGateway: FindUserByIdGateway,
    private val rotateRefreshTokenGateway: RotateRefreshTokenGateway,
    private val revokeTokenFamilyGateway: RevokeTokenFamilyGateway,
    private val meterRegistry: MeterRegistry,
) {

    fun execute(refreshToken: String): Result<AuthSession> {
        return runCatching {
            val tokenHash = TokenHasher.sha256(refreshToken)
            val stored = findRefreshTokenGateway.execute(tokenHash).getOrThrow()
                ?: throw InvalidRefreshTokenException()
            if (stored.isRotatedOrRevoked()) {
                // Reuse of a rotated token indicates possible theft: revoke the whole family
                val revoked = revokeTokenFamilyGateway.execute(stored).getOrThrow()
                meterRegistry.counter("auth.refresh.reuse").increment()
                logger.warn("Refresh token reuse detected for user {}; revoked {} token(s) in family", stored.userId, revoked)
                throw InvalidRefreshTokenException()
            }
            if (stored.isExpired(Instant.now())) {
                throw InvalidRefreshTokenException()
            }
            val user = findUserByIdGateway.execute(stored.userId).getOrThrow()
                ?: throw InvalidRefreshTokenException()
            rotateRefreshTokenGateway.execute(stored, user).getOrThrow()
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(RefreshSessionUseCase::class.java)
    }
}
