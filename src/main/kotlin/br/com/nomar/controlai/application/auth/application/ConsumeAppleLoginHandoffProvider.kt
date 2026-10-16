package br.com.nomar.controlai.application.auth.application

import br.com.nomar.controlai.application.auth.entrypoint.database.repository.AppleLoginFlowRepository
import br.com.nomar.controlai.domain.auth.gateway.ConsumeAppleLoginHandoffGateway
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class ConsumeAppleLoginHandoffProvider(
    private val appleLoginFlowRepository: AppleLoginFlowRepository,
) : ConsumeAppleLoginHandoffGateway {

    override fun execute(flowId: Long): Result<Boolean> {
        return runCatching {
            appleLoginFlowRepository.consumeHandoffIfUnused(flowId, Instant.now()) == 1
        }
    }
}
