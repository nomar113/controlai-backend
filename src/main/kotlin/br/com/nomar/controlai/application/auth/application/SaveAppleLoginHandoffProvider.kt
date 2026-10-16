package br.com.nomar.controlai.application.auth.application

import br.com.nomar.controlai.application.auth.entrypoint.database.repository.AppleLoginFlowRepository
import br.com.nomar.controlai.domain.auth.gateway.SaveAppleLoginHandoffGateway
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class SaveAppleLoginHandoffProvider(
    private val appleLoginFlowRepository: AppleLoginFlowRepository,
) : SaveAppleLoginHandoffGateway {

    override fun execute(flowId: Long, handoffCodeHash: String, userId: Long, handoffExpiresAt: Instant): Result<Boolean> {
        return runCatching {
            appleLoginFlowRepository.saveHandoffIfNonceUnused(
                id = flowId,
                now = Instant.now(),
                handoffCodeHash = handoffCodeHash,
                userId = userId,
                handoffExpiresAt = handoffExpiresAt,
            ) == 1
        }
    }
}
