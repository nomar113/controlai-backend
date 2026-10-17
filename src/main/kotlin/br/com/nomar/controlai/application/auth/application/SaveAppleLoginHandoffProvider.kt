package br.com.nomar.controlai.application.auth.application

import br.com.nomar.controlai.application.auth.entrypoint.database.repository.AppleLoginFlowRepository
import br.com.nomar.controlai.domain.auth.entity.AppleLoginHandoff
import br.com.nomar.controlai.domain.auth.gateway.SaveAppleLoginHandoffGateway
import org.springframework.stereotype.Component

@Component
class SaveAppleLoginHandoffProvider(
    private val appleLoginFlowRepository: AppleLoginFlowRepository,
) : SaveAppleLoginHandoffGateway {

    override fun execute(handoff: AppleLoginHandoff): Result<Boolean> {
        return runCatching { appleLoginFlowRepository.saveHandoffIfNonceUnused(handoff) == 1 }
    }
}
