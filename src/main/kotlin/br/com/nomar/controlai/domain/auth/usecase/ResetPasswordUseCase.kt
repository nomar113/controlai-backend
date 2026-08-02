package br.com.nomar.controlai.domain.auth.usecase

import br.com.nomar.controlai.domain.auth.TokenHasher
import br.com.nomar.controlai.domain.auth.exception.InvalidResetTokenException
import br.com.nomar.controlai.domain.auth.gateway.FindPasswordResetTokenByHashGateway
import br.com.nomar.controlai.domain.auth.gateway.MarkPasswordResetTokenUsedGateway
import br.com.nomar.controlai.domain.auth.gateway.PasswordHashGateway
import br.com.nomar.controlai.domain.auth.gateway.RevokeAllRefreshTokensByUserIdGateway
import br.com.nomar.controlai.domain.auth.gateway.UpdateUserPasswordGateway
import org.springframework.stereotype.Component

@Component
class ResetPasswordUseCase(
    private val findPasswordResetTokenByHashGateway: FindPasswordResetTokenByHashGateway,
    private val markPasswordResetTokenUsedGateway: MarkPasswordResetTokenUsedGateway,
    private val passwordHashGateway: PasswordHashGateway,
    private val updateUserPasswordGateway: UpdateUserPasswordGateway,
    private val revokeAllRefreshTokensByUserIdGateway: RevokeAllRefreshTokensByUserIdGateway,
) {

    fun execute(rawToken: String, newPassword: String): Result<Unit> {
        return runCatching {
            require(newPassword.length >= MIN_PASSWORD_LENGTH) {
                "Password must be at least $MIN_PASSWORD_LENGTH characters"
            }

            val tokenHash = TokenHasher.sha256(rawToken)
            val resetToken = findPasswordResetTokenByHashGateway.execute(tokenHash).getOrThrow()
                ?: throw InvalidResetTokenException()

            if (!resetToken.isValid()) throw InvalidResetTokenException()

            val newHash = passwordHashGateway.hash(newPassword)
            updateUserPasswordGateway.execute(resetToken.userId, newHash).getOrThrow()
            markPasswordResetTokenUsedGateway.execute(resetToken.id!!).getOrThrow()
            // Revoke all active sessions so the user must log in with the new password
            revokeAllRefreshTokensByUserIdGateway.execute(resetToken.userId).getOrThrow()
        }
    }

    companion object {
        const val MIN_PASSWORD_LENGTH = 8
    }
}
