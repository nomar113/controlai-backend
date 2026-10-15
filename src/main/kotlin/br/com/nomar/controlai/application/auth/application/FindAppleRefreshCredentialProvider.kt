package br.com.nomar.controlai.application.auth.application

import br.com.nomar.controlai.application.auth.entrypoint.database.repository.UserIdentityRepository
import br.com.nomar.controlai.domain.auth.entity.IdentityProvider
import br.com.nomar.controlai.domain.auth.gateway.FindAppleRefreshCredentialGateway
import org.springframework.stereotype.Component

@Component
class FindAppleRefreshCredentialProvider(
    private val userIdentityRepository: UserIdentityRepository,
) : FindAppleRefreshCredentialGateway {

    override fun execute(userId: Long): Result<String?> {
        return runCatching {
            userIdentityRepository.findByUserIdAndProvider(userId, IdentityProvider.APPLE)?.appleRefreshTokenEnc
        }
    }
}
