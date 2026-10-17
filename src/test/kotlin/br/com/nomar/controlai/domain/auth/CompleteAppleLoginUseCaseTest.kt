package br.com.nomar.controlai.domain.auth

import br.com.nomar.controlai.domain.auth.entity.AppleCallback
import br.com.nomar.controlai.domain.auth.entity.AppleLoginCommand
import br.com.nomar.controlai.domain.auth.entity.AppleLoginFlow
import br.com.nomar.controlai.domain.auth.entity.AppleLoginPlatform
import br.com.nomar.controlai.domain.auth.entity.User
import br.com.nomar.controlai.domain.auth.usecase.AppleLoginUseCase
import br.com.nomar.controlai.domain.auth.usecase.CompleteAppleLoginUseCase
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CompleteAppleLoginUseCaseTest {

    private val flow = AppleLoginFlow(
        id = 7,
        nonce = "raw-nonce",
        state = "expected-state",
        platform = AppleLoginPlatform.WEB,
        expiresAt = Instant.now().plusSeconds(600),
        consumedAt = null,
        userId = null,
        handoffExpiresAt = null,
        handoffConsumedAt = null,
    )

    private val user = User(id = 42, name = "Maria", email = "maria@example.com", passwordHash = "hashed")

    private val savedFlowIds = mutableListOf<Long>()

    // Mockito's any() returns null, which Kotlin rejects for non-null parameters; this registers the matcher and
    // returns a placeholder instead
    private fun anyCommand(): AppleLoginCommand {
        ArgumentMatchers.any(AppleLoginCommand::class.java)
        return AppleLoginCommand(identityToken = "", authorizationCode = "", nonce = "", fullName = null)
    }

    private fun useCase(
        appleLogin: AppleLoginUseCase = Mockito.mock(AppleLoginUseCase::class.java),
        flowByState: AppleLoginFlow? = null,
    ) = CompleteAppleLoginUseCase(
        readUnverifiedNonceClaimGateway = { Result.success("raw-nonce") },
        findAppleLoginFlowByNonceDigestGateway = { Result.success(flow) },
        findAppleLoginFlowByStateGateway = { Result.success(flowByState) },
        saveAppleLoginHandoffGateway = { handoff ->
            savedFlowIds.add(handoff.flowId)
            Result.success(true)
        },
        appleLoginUseCase = appleLogin,
        androidReturnUri = "controlai://auth/apple-login",
        appWebUrl = "https://app.example.com/",
    )

    private fun callback(state: String?, identityToken: String? = "identity-token") = AppleCallback(
        authorizationCode = "auth-code",
        identityToken = identityToken,
        fullName = "Maria Souza",
        state = state,
    )

    @Test
    fun `state ausente nao troca o codigo, nao cria conta e redireciona com erro`() {
        val appleLogin = Mockito.mock(AppleLoginUseCase::class.java)

        val location = useCase(appleLogin).execute(callback(state = null))

        assertEquals("https://app.example.com/login?error=apple_login_failed", location)
        assertTrue(savedFlowIds.isEmpty())
        Mockito.verifyNoInteractions(appleLogin)
    }

    @Test
    fun `state divergente do guardado no fluxo nao gera codigo nem cria conta`() {
        val appleLogin = Mockito.mock(AppleLoginUseCase::class.java)

        val location = useCase(appleLogin).execute(callback(state = "state-do-atacante"))

        assertFalse(location.contains("state-do-atacante"), location)
        assertTrue(location.contains("error=apple_login_failed"), location)
        assertTrue(savedFlowIds.isEmpty())
        Mockito.verifyNoInteractions(appleLogin)
    }

    @Test
    fun `state igual ao guardado gera codigo, devolve o state e grava o handoff`() {
        val appleLogin = Mockito.mock(AppleLoginUseCase::class.java)
        Mockito.`when`(appleLogin.resolveUser(anyCommand()))
            .thenReturn(Result.success(user))

        val location = useCase(appleLogin).execute(callback(state = "expected-state"))

        assertTrue(location.startsWith("https://app.example.com/login?code="), location)
        assertTrue(location.endsWith("&state=expected-state"), location)
        assertEquals(listOf(7L), savedFlowIds)
    }

    @Test
    fun `cancelamento sem id_token usa o fluxo do state para voltar ao app certo`() {
        val androidFlow = AppleLoginFlow(
            id = 8,
            nonce = "raw-nonce",
            state = "expected-state",
            platform = AppleLoginPlatform.ANDROID,
            expiresAt = Instant.now().plusSeconds(600),
            consumedAt = null,
            userId = null,
            handoffExpiresAt = null,
            handoffConsumedAt = null,
        )

        val location = useCase(flowByState = androidFlow).execute(callback(state = "expected-state", identityToken = null))

        assertEquals("controlai://auth/apple-login?error=apple_login_failed&state=expected-state", location)
    }

    @Test
    fun `state com caracteres de redirect nao chega a URL de erro`() {
        val location = useCase().execute(callback(state = "x&code=injetado y"))

        assertEquals("https://app.example.com/login?error=apple_login_failed", location)
    }
}
