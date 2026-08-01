package br.com.nomar.controlai.application.auth.application

import br.com.nomar.controlai.application.auth.converter.UserConverter
import br.com.nomar.controlai.application.auth.entrypoint.database.repository.UserRepository
import br.com.nomar.controlai.domain.auth.entity.User
import br.com.nomar.controlai.domain.auth.gateway.FindUserByEmailGateway
import org.springframework.stereotype.Component

@Component
class FindUserByEmailProvider(
    private val userRepository: UserRepository,
    private val converter: UserConverter,
) : FindUserByEmailGateway {

    override fun execute(email: String): Result<User?> {
        return runCatching {
            userRepository.findByEmail(email)?.let(converter::toEntity)
        }
    }
}
