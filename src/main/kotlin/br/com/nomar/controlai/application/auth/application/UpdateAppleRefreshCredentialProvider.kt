package br.com.nomar.controlai.application.auth.application

import br.com.nomar.controlai.application.auth.entrypoint.database.repository.UserIdentityRepository
import br.com.nomar.controlai.domain.auth.entity.IdentityProvider
import br.com.nomar.controlai.domain.auth.gateway.UpdateAppleRefreshCredentialGateway
import org.springframework.stereotype.Component

@Component
class UpdateAppleRefreshCredentialProvider(
    private val userIdentityRepository: UserIdentityRepository,
) : UpdateAppleRefreshCredentialGateway {

    override fun execute(userId: Long, encryptedCredential: String?): Result<Unit> {
        return runCatching {
            val identity = userIdentityRepository.findByUserIdAndProvider(userId, IdentityProvider.APPLE)
                ?: throw IllegalStateException("Usuario sem identidade Apple")
            userIdentityRepository.save(identity.copy(appleRefreshTokenEnc = encryptedCredential))
            Unit
        }
    }
}
