package br.com.nomar.controlai.domain.auth.usecase

import br.com.nomar.controlai.domain.auth.entity.AuthSession
import br.com.nomar.controlai.domain.auth.entity.User
import br.com.nomar.controlai.domain.auth.exception.EmailAlreadyUsedException
import br.com.nomar.controlai.domain.auth.gateway.CreateUserWithPersonalGroupGateway
import br.com.nomar.controlai.domain.auth.gateway.FindUserByEmailGateway
import br.com.nomar.controlai.domain.auth.gateway.IssueTokensGateway
import br.com.nomar.controlai.domain.auth.gateway.PasswordHashGateway
import org.springframework.stereotype.Component

@Component
class RegisterUserUseCase(
    private val findUserByEmailGateway: FindUserByEmailGateway,
    private val passwordHashGateway: PasswordHashGateway,
    private val createUserWithPersonalGroupGateway: CreateUserWithPersonalGroupGateway,
    private val issueTokensGateway: IssueTokensGateway,
) {

    fun execute(name: String, email: String, password: String): Result<AuthSession> {
        return runCatching {
            require(password.length >= MIN_PASSWORD_LENGTH) { "A senha deve ter no minimo $MIN_PASSWORD_LENGTH caracteres" }
            val normalizedEmail = email.trim().lowercase()
            findUserByEmailGateway.execute(normalizedEmail).getOrThrow()?.let {
                throw EmailAlreadyUsedException()
            }
            val user = createUserWithPersonalGroupGateway.execute(
                User(
                    name = name.trim(),
                    email = normalizedEmail,
                    passwordHash = passwordHashGateway.hash(password),
                ),
            ).getOrThrow()
            issueTokensGateway.execute(user).getOrThrow()
        }
    }

    companion object {
        const val MIN_PASSWORD_LENGTH = 8
    }
}
