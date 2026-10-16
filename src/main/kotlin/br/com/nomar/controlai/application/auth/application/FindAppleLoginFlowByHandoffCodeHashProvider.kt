package br.com.nomar.controlai.application.auth.application

import br.com.nomar.controlai.application.auth.converter.toDomain
import br.com.nomar.controlai.application.auth.entrypoint.database.repository.AppleLoginFlowRepository
import br.com.nomar.controlai.domain.auth.entity.AppleLoginFlow
import br.com.nomar.controlai.domain.auth.gateway.FindAppleLoginFlowByHandoffCodeHashGateway
import org.springframework.stereotype.Component

@Component
class FindAppleLoginFlowByHandoffCodeHashProvider(
    private val appleLoginFlowRepository: AppleLoginFlowRepository,
) : FindAppleLoginFlowByHandoffCodeHashGateway {

    override fun execute(handoffCodeHash: String): Result<AppleLoginFlow?> {
        return runCatching { appleLoginFlowRepository.findByHandoffCodeHash(handoffCodeHash)?.toDomain() }
    }
}
