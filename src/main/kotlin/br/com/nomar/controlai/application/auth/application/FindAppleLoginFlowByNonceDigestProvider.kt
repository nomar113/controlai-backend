package br.com.nomar.controlai.application.auth.application

import br.com.nomar.controlai.application.auth.converter.toDomain
import br.com.nomar.controlai.application.auth.entrypoint.database.repository.AppleLoginFlowRepository
import br.com.nomar.controlai.domain.auth.entity.AppleLoginFlow
import br.com.nomar.controlai.domain.auth.gateway.FindAppleLoginFlowByNonceDigestGateway
import org.springframework.stereotype.Component

@Component
class FindAppleLoginFlowByNonceDigestProvider(
    private val appleLoginFlowRepository: AppleLoginFlowRepository,
) : FindAppleLoginFlowByNonceDigestGateway {

    override fun execute(nonceDigest: String): Result<AppleLoginFlow?> {
        return runCatching { appleLoginFlowRepository.findByNonceDigest(nonceDigest)?.toDomain() }
    }
}
