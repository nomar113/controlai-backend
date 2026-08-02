package br.com.nomar.controlai.application.auth.application

import br.com.nomar.controlai.application.auth.converter.UserConverter
import br.com.nomar.controlai.application.auth.entrypoint.database.repository.UserRepository
import br.com.nomar.controlai.domain.auth.entity.User
import br.com.nomar.controlai.domain.auth.gateway.LinkGoogleSubToUserGateway
import org.springframework.stereotype.Component

@Component
class LinkGoogleSubToUserProvider(
    private val userRepository: UserRepository,
    private val userConverter: UserConverter,
) : LinkGoogleSubToUserGateway {

    override fun execute(userId: Long, googleSub: String): Result<User> {
        return runCatching {
            val model = userRepository.getReferenceById(userId)
            val updated = userRepository.save(model.copy(googleSub = googleSub))
            userConverter.toEntity(updated)
        }
    }
}
