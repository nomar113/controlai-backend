package br.com.nomar.controlai.domain.auth.usecase

import br.com.nomar.controlai.domain.auth.exception.InvalidCredentialsException
import br.com.nomar.controlai.domain.auth.gateway.FindUserByIdGateway
import br.com.nomar.controlai.domain.auth.gateway.PasswordHashGateway
import br.com.nomar.controlai.domain.auth.gateway.UpdateUserPasswordGateway
import org.springframework.stereotype.Component

@Component
class ChangePasswordUseCase(
    private val findUserByIdGateway: FindUserByIdGateway,
    private val passwordHashGateway: PasswordHashGateway,
    private val updateUserPasswordGateway: UpdateUserPasswordGateway,
) {

    fun execute(userId: Long, currentPassword: String, newPassword: String): Result<Unit> {
        return runCatching {
            require(newPassword.length >= MIN_PASSWORD_LENGTH) {
                "Password must be at least $MIN_PASSWORD_LENGTH characters"
            }

            val user = findUserByIdGateway.execute(userId).getOrThrow()
                ?: throw InvalidCredentialsException()

            // Google-only accounts have no password; they must use the reset flow (RF-5.4)
            if (user.passwordHash == null || !passwordHashGateway.matches(currentPassword, user.passwordHash)) {
                throw InvalidCredentialsException()
            }

            val newHash = passwordHashGateway.hash(newPassword)
            updateUserPasswordGateway.execute(userId, newHash).getOrThrow()
        }
    }

    companion object {
        const val MIN_PASSWORD_LENGTH = 8
    }
}
