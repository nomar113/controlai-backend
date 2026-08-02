package br.com.nomar.controlai.domain.auth.usecase

import br.com.nomar.controlai.domain.auth.TokenHasher
import br.com.nomar.controlai.domain.auth.entity.PasswordResetToken
import br.com.nomar.controlai.domain.auth.gateway.CreatePasswordResetTokenGateway
import br.com.nomar.controlai.domain.auth.gateway.EmailGateway
import br.com.nomar.controlai.domain.auth.gateway.FindUserByEmailGateway
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@Component
class ForgotPasswordUseCase(
    private val findUserByEmailGateway: FindUserByEmailGateway,
    private val createPasswordResetTokenGateway: CreatePasswordResetTokenGateway,
    private val emailGateway: EmailGateway,
    @Value("\${app.web-url}") private val appWebUrl: String,
) {

    // Always returns success to avoid revealing whether the email exists (RF-2.3)
    fun execute(email: String): Result<Unit> {
        return runCatching {
            val normalizedEmail = email.trim().lowercase()
            val user = findUserByEmailGateway.execute(normalizedEmail).getOrThrow()

            if (user?.id != null) {
                val rawToken = UUID.randomUUID().toString()
                val tokenHash = TokenHasher.sha256(rawToken)
                val token = PasswordResetToken(
                    userId = user.id,
                    tokenHash = tokenHash,
                    expiresAt = Instant.now().plus(TOKEN_TTL_HOURS, ChronoUnit.HOURS),
                )
                createPasswordResetTokenGateway.execute(token).getOrThrow()

                val resetLink = "$appWebUrl/reset-password?token=$rawToken"
                emailGateway.sendPasswordReset(user.email, user.name, resetLink)
                    .onFailure { logger.error("Failed to send password reset email for user {}", user.id, it) }
            }
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ForgotPasswordUseCase::class.java)
        private const val TOKEN_TTL_HOURS = 1L
    }
}
