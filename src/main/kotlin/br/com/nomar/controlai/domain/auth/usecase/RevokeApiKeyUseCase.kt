package br.com.nomar.controlai.domain.auth.usecase

import br.com.nomar.controlai.domain.auth.gateway.FindApiKeyByGroupIdGateway
import br.com.nomar.controlai.domain.auth.gateway.RevokeApiKeyGateway
import org.springframework.stereotype.Component

@Component
class RevokeApiKeyUseCase(
    private val findApiKeyByGroupIdGateway: FindApiKeyByGroupIdGateway,
    private val revokeApiKeyGateway: RevokeApiKeyGateway,
) {

    fun execute(groupId: Long): Result<Unit> {
        return runCatching {
            val existing = findApiKeyByGroupIdGateway.execute(groupId).getOrThrow()
                ?: throw NoSuchElementException("No API key found for this group")
            if (existing.isRevoked()) {
                throw IllegalStateException("API key is already revoked")
            }
            revokeApiKeyGateway.revokeByGroupId(groupId).getOrThrow()
        }
    }
}
