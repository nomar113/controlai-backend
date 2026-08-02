package br.com.nomar.controlai.application.auth.application

import br.com.nomar.controlai.application.auth.entrypoint.database.repository.UserRepository
import br.com.nomar.controlai.domain.auth.gateway.UpdateUserPasswordGateway
import org.springframework.stereotype.Component

@Component
class UpdateUserPasswordProvider(
    private val userRepository: UserRepository,
) : UpdateUserPasswordGateway {

    override fun execute(userId: Long, newPasswordHash: String): Result<Unit> {
        return runCatching {
            val user = userRepository.findById(userId).orElseThrow {
                NoSuchElementException("Usuario nao encontrado: $userId")
            }
            userRepository.save(user.copy(passwordHash = newPasswordHash))
            Unit
        }
    }
}
