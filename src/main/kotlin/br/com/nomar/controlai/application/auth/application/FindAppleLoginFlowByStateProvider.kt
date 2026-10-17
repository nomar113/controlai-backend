package br.com.nomar.controlai.application.auth.application

import br.com.nomar.controlai.application.auth.converter.toDomain
import br.com.nomar.controlai.application.auth.entrypoint.database.repository.AppleLoginFlowRepository
import br.com.nomar.controlai.domain.auth.entity.AppleLoginFlow
import br.com.nomar.controlai.domain.auth.gateway.FindAppleLoginFlowByStateGateway
import org.springframework.stereotype.Component

@Component
class FindAppleLoginFlowByStateProvider(
    private val appleLoginFlowRepository: AppleLoginFlowRepository,
) : FindAppleLoginFlowByStateGateway {

    override fun execute(state: String): Result<AppleLoginFlow?> {
        return runCatching { appleLoginFlowRepository.findFirstByState(state)?.toDomain() }
    }
}
