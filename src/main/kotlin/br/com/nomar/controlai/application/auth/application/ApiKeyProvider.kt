package br.com.nomar.controlai.application.auth.application

import br.com.nomar.controlai.application.auth.entrypoint.database.model.ApiKeyModel
import br.com.nomar.controlai.application.auth.entrypoint.database.repository.ApiKeyRepository
import br.com.nomar.controlai.domain.auth.entity.ApiKey
import br.com.nomar.controlai.domain.auth.gateway.CreateApiKeyGateway
import br.com.nomar.controlai.domain.auth.gateway.FindApiKeyByGroupIdGateway
import br.com.nomar.controlai.domain.auth.gateway.FindApiKeyByHashGateway
import br.com.nomar.controlai.domain.auth.gateway.RevokeApiKeyGateway
import org.springframework.stereotype.Component
import java.time.Instant

// FindApiKeyByGroupIdGateway and FindApiKeyByHashGateway are in the same class because
// their SAM methods have different parameter types (Long vs String) — no JVM clash.
@Component
class FindApiKeyProvider(
    private val apiKeyRepository: ApiKeyRepository,
) : FindApiKeyByGroupIdGateway, FindApiKeyByHashGateway {

    override fun execute(groupId: Long): Result<ApiKey?> =
        runCatching { apiKeyRepository.findByGroupId(groupId)?.toDomain() }

    override fun execute(keyHash: String): Result<ApiKey?> =
        runCatching { apiKeyRepository.findByKeyHash(keyHash)?.toDomain() }
}

@Component
class CreateApiKeyProvider(
    private val apiKeyRepository: ApiKeyRepository,
) : CreateApiKeyGateway {

    override fun execute(apiKey: ApiKey): Result<ApiKey> =
        runCatching { apiKeyRepository.save(apiKey.toModel()).toDomain() }
}

@Component
class RevokeApiKeyProvider(
    private val apiKeyRepository: ApiKeyRepository,
) : RevokeApiKeyGateway {

    override fun revokeByGroupId(groupId: Long): Result<Unit> =
        runCatching {
            apiKeyRepository.findByGroupId(groupId)?.let { model ->
                if (model.revokedAt == null) {
                    apiKeyRepository.save(model.copy(revokedAt = Instant.now()))
                }
            }
            Unit
        }
}

// ── converters ────────────────────────────────────────────────────────────────

private fun ApiKeyModel.toDomain() = ApiKey(
    id = id,
    groupId = groupId,
    keyHash = keyHash,
    label = label,
    createdAt = createdAt,
    revokedAt = revokedAt,
)

private fun ApiKey.toModel() = ApiKeyModel(
    id = id,
    groupId = groupId,
    keyHash = keyHash,
    label = label,
    revokedAt = revokedAt,
)
