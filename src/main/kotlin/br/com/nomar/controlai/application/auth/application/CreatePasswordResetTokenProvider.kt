package br.com.nomar.controlai.application.auth.application

import br.com.nomar.controlai.application.auth.entrypoint.database.model.PasswordResetTokenModel
import br.com.nomar.controlai.application.auth.entrypoint.database.repository.PasswordResetTokenRepository
import br.com.nomar.controlai.domain.auth.entity.PasswordResetToken
import br.com.nomar.controlai.domain.auth.gateway.CreatePasswordResetTokenGateway
import org.springframework.stereotype.Component

@Component
class CreatePasswordResetTokenProvider(
    private val passwordResetTokenRepository: PasswordResetTokenRepository,
) : CreatePasswordResetTokenGateway {

    override fun execute(token: PasswordResetToken): Result<PasswordResetToken> {
        return runCatching {
            val model = PasswordResetTokenModel(
                userId = token.userId,
                tokenHash = token.tokenHash,
                expiresAt = token.expiresAt,
            )
            val saved = passwordResetTokenRepository.save(model)
            PasswordResetToken(
                id = saved.id,
                userId = saved.userId,
                tokenHash = saved.tokenHash,
                expiresAt = saved.expiresAt,
                usedAt = saved.usedAt,
            )
        }
    }
}
