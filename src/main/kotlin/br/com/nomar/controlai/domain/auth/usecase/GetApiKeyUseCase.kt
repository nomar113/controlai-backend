package br.com.nomar.controlai.domain.auth.usecase

import br.com.nomar.controlai.domain.auth.entity.ApiKey
import br.com.nomar.controlai.domain.auth.gateway.FindApiKeyByGroupIdGateway
import org.springframework.stereotype.Component

@Component
class GetApiKeyUseCase(
    private val findApiKeyByGroupIdGateway: FindApiKeyByGroupIdGateway,
) {

    fun execute(groupId: Long): Result<ApiKey?> =
        findApiKeyByGroupIdGateway.execute(groupId)
}
