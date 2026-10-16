package br.com.nomar.controlai.application.auth.application

import br.com.nomar.controlai.application.auth.entrypoint.database.model.AppleLoginFlowModel
import br.com.nomar.controlai.application.auth.entrypoint.database.repository.AppleLoginFlowRepository
import br.com.nomar.controlai.domain.auth.TokenHasher
import br.com.nomar.controlai.domain.auth.entity.AppleLoginPlatform
import br.com.nomar.controlai.domain.auth.gateway.CreateAppleLoginFlowGateway
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class CreateAppleLoginFlowProvider(
    private val appleLoginFlowRepository: AppleLoginFlowRepository,
) : CreateAppleLoginFlowGateway {

    override fun execute(nonce: String, state: String, platform: AppleLoginPlatform, expiresAt: Instant): Result<Unit> {
        return runCatching {
            appleLoginFlowRepository.save(
                AppleLoginFlowModel(
                    nonce = nonce,
                    nonceDigest = TokenHasher.sha256(nonce),
                    state = state,
                    platform = platform,
                    expiresAt = expiresAt,
                ),
            )
            Unit
        }
    }
}
