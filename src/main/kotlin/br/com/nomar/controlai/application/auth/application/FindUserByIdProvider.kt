package br.com.nomar.controlai.application.auth.application

import br.com.nomar.controlai.application.auth.converter.UserConverter
import br.com.nomar.controlai.application.auth.entrypoint.database.repository.UserRepository
import br.com.nomar.controlai.domain.auth.entity.User
import br.com.nomar.controlai.domain.auth.gateway.FindUserByIdGateway
import org.springframework.stereotype.Component

@Component
class FindUserByIdProvider(
    private val userRepository: UserRepository,
    private val converter: UserConverter,
) : FindUserByIdGateway {

    override fun execute(id: Long): Result<User?> {
        return runCatching {
            userRepository.findById(id).orElse(null)?.let(converter::toEntity)
        }
    }
}
