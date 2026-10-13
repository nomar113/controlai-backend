package br.com.nomar.controlai.application.auth.application

import br.com.nomar.controlai.application.auth.converter.UserConverter
import br.com.nomar.controlai.application.auth.entrypoint.database.repository.UserIdentityRepository
import br.com.nomar.controlai.application.auth.entrypoint.database.repository.UserRepository
import br.com.nomar.controlai.domain.auth.entity.IdentityProvider
import br.com.nomar.controlai.domain.auth.entity.User
import br.com.nomar.controlai.domain.auth.gateway.FindUserByIdentityGateway
import org.springframework.stereotype.Component

@Component
class FindUserByIdentityProvider(
    private val userIdentityRepository: UserIdentityRepository,
    private val userRepository: UserRepository,
    private val userConverter: UserConverter,
) : FindUserByIdentityGateway {

    override fun execute(provider: IdentityProvider, subject: String): Result<User?> {
        return runCatching {
            userIdentityRepository.findByProviderAndSubject(provider, subject)
                ?.let { userRepository.findById(it.userId).orElse(null) }
                ?.let(userConverter::toEntity)
        }
    }
}
