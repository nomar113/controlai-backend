package br.com.nomar.controlai.domain.auth

import br.com.nomar.controlai.domain.auth.entity.ApiKey
import br.com.nomar.controlai.domain.auth.usecase.CreateApiKeyUseCase
import br.com.nomar.controlai.domain.auth.usecase.GetApiKeyUseCase
import br.com.nomar.controlai.domain.auth.usecase.RevokeApiKeyUseCase
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ApiKeyUseCasesTest {

    private fun savedKey(groupId: Long = 1, revokedAt: Instant? = null) = ApiKey(
        id = 10,
        groupId = groupId,
        keyHash = "hash",
        label = "iPhone Shortcut",
        revokedAt = revokedAt,
    )

    // ── CreateApiKeyUseCase ──────────────────────────────────────────────────

    @Test
    fun `CreateApiKeyUseCase should generate a raw key and store its hash`() {
        var stored: ApiKey? = null
        val useCase = CreateApiKeyUseCase(
            findApiKeyByGroupIdGateway = { Result.success(null) },
            createApiKeyGateway = { key ->
                stored = key
                Result.success(key.copy(id = 1))
            },
        )

        val result = useCase.execute(groupId = 1)

        assertTrue(result.isSuccess)
        val (_, rawKey) = result.getOrThrow()
        assertTrue(rawKey.startsWith("cap_"))
        assertNotNull(stored)
        assertEquals(TokenHasher.sha256(rawKey), stored!!.keyHash)
    }

    @Test
    fun `CreateApiKeyUseCase should fail when an active key already exists`() {
        val useCase = CreateApiKeyUseCase(
            findApiKeyByGroupIdGateway = { Result.success(savedKey()) },
            createApiKeyGateway = { Result.success(it.copy(id = 1)) },
        )

        val result = useCase.execute(groupId = 1)

        assertTrue(result.isFailure)
        assertIs<IllegalStateException>(result.exceptionOrNull())
    }

    @Test
    fun `CreateApiKeyUseCase should succeed when existing key is already revoked`() {
        val revokedKey = savedKey(revokedAt = Instant.now())
        val useCase = CreateApiKeyUseCase(
            findApiKeyByGroupIdGateway = { Result.success(revokedKey) },
            createApiKeyGateway = { Result.success(it.copy(id = 2)) },
        )

        val result = useCase.execute(groupId = 1)

        assertTrue(result.isSuccess)
    }

    @Test
    fun `CreateApiKeyUseCase raw key should be different on each call`() {
        val useCase = CreateApiKeyUseCase(
            findApiKeyByGroupIdGateway = { Result.success(null) },
            createApiKeyGateway = { Result.success(it.copy(id = 1)) },
        )

        val key1 = useCase.execute(1).getOrThrow().second
        val key2 = useCase.execute(1).getOrThrow().second

        assertFalse(key1 == key2)
    }

    // ── GetApiKeyUseCase ─────────────────────────────────────────────────────

    @Test
    fun `GetApiKeyUseCase should return null when no key exists`() {
        val useCase = GetApiKeyUseCase(findApiKeyByGroupIdGateway = { Result.success(null) })
        val result = useCase.execute(groupId = 1)
        assertTrue(result.isSuccess)
        assertEquals(null, result.getOrThrow())
    }

    @Test
    fun `GetApiKeyUseCase should return existing key without exposing raw value`() {
        val key = savedKey()
        val useCase = GetApiKeyUseCase(findApiKeyByGroupIdGateway = { Result.success(key) })
        val result = useCase.execute(groupId = 1)
        assertTrue(result.isSuccess)
        assertEquals("hash", result.getOrThrow()?.keyHash)
    }

    // ── RevokeApiKeyUseCase ──────────────────────────────────────────────────

    @Test
    fun `RevokeApiKeyUseCase should revoke an existing active key`() {
        var revoked = false
        val useCase = RevokeApiKeyUseCase(
            findApiKeyByGroupIdGateway = { Result.success(savedKey()) },
            revokeApiKeyGateway = {
                revoked = true
                Result.success(Unit)
            },
        )

        val result = useCase.execute(groupId = 1)

        assertTrue(result.isSuccess)
        assertTrue(revoked)
    }

    @Test
    fun `RevokeApiKeyUseCase should fail when no key exists`() {
        val useCase = RevokeApiKeyUseCase(
            findApiKeyByGroupIdGateway = { Result.success(null) },
            revokeApiKeyGateway = { Result.success(Unit) },
        )

        val result = useCase.execute(groupId = 1)

        assertTrue(result.isFailure)
        assertIs<NoSuchElementException>(result.exceptionOrNull())
    }

    @Test
    fun `RevokeApiKeyUseCase should fail when key is already revoked`() {
        val useCase = RevokeApiKeyUseCase(
            findApiKeyByGroupIdGateway = { Result.success(savedKey(revokedAt = Instant.now())) },
            revokeApiKeyGateway = { Result.success(Unit) },
        )

        val result = useCase.execute(groupId = 1)

        assertTrue(result.isFailure)
        assertIs<IllegalStateException>(result.exceptionOrNull())
    }
}
