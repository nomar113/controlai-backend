package br.com.nomar.controlai.application.auth.application

import br.com.nomar.controlai.application.auth.entrypoint.database.repository.PasswordResetTokenRepository
import br.com.nomar.controlai.domain.auth.entity.PasswordResetToken
import br.com.nomar.controlai.domain.auth.gateway.FindPasswordResetTokenByHashGateway
import org.springframework.stereotype.Component

@Component
class FindPasswordResetTokenByHashProvider(
    private val passwordResetTokenRepository: PasswordResetTokenRepository,
) : FindPasswordResetTokenByHashGateway {

    override fun execute(tokenHash: String): Result<PasswordResetToken?> {
        return runCatching {
            passwordResetTokenRepository.findByTokenHash(tokenHash)?.let { model ->
                PasswordResetToken(
                    id = model.id,
                    userId = model.userId,
                    tokenHash = model.tokenHash,
                    expiresAt = model.expiresAt,
                    usedAt = model.usedAt,
                )
            }
        }
    }
}
