package br.com.nomar.controlai.application.auth.application

import br.com.nomar.controlai.application.auth.converter.UserConverter
import br.com.nomar.controlai.application.auth.entrypoint.database.repository.UserRepository
import br.com.nomar.controlai.domain.auth.entity.User
import br.com.nomar.controlai.domain.auth.gateway.FindUserByGoogleSubGateway
import org.springframework.stereotype.Component

@Component
class FindUserByGoogleSubProvider(
    private val userRepository: UserRepository,
    private val userConverter: UserConverter,
) : FindUserByGoogleSubGateway {

    override fun execute(googleSub: String): Result<User?> {
        return runCatching {
            userRepository.findByGoogleSub(googleSub)?.let(userConverter::toEntity)
        }
    }
}
