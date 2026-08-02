package br.com.nomar.controlai.domain.auth.usecase

import br.com.nomar.controlai.domain.auth.TokenHasher
import br.com.nomar.controlai.domain.auth.entity.ApiKey
import br.com.nomar.controlai.domain.auth.gateway.CreateApiKeyGateway
import br.com.nomar.controlai.domain.auth.gateway.FindApiKeyByGroupIdGateway
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class CreateApiKeyUseCase(
    private val findApiKeyByGroupIdGateway: FindApiKeyByGroupIdGateway,
    private val createApiKeyGateway: CreateApiKeyGateway,
) {

    // Returns (saved ApiKey, raw key value). Raw value shown only at creation time.
    fun execute(groupId: Long, label: String = "iPhone Shortcut"): Result<Pair<ApiKey, String>> {
        return runCatching {
            val existing = findApiKeyByGroupIdGateway.execute(groupId).getOrThrow()
            if (existing != null && !existing.isRevoked()) {
                throw IllegalStateException("An active API key already exists. Revoke it before creating a new one.")
            }

            val rawKey = "cap_" + UUID.randomUUID().toString().replace("-", "")
            val keyHash = TokenHasher.sha256(rawKey)
            val saved = createApiKeyGateway.execute(
                ApiKey(groupId = groupId, keyHash = keyHash, label = label)
            ).getOrThrow()

            Pair(saved, rawKey)
        }
    }
}
