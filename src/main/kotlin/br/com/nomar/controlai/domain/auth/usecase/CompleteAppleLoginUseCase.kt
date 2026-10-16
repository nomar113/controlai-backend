package br.com.nomar.controlai.domain.auth.usecase

import br.com.nomar.controlai.domain.auth.OpaqueTokens
import br.com.nomar.controlai.domain.auth.TokenHasher
import br.com.nomar.controlai.domain.auth.entity.AppleLoginCommand
import br.com.nomar.controlai.domain.auth.entity.AppleLoginFlow
import br.com.nomar.controlai.domain.auth.entity.AppleLoginPlatform
import br.com.nomar.controlai.domain.auth.exception.InvalidAppleLoginFlowException
import br.com.nomar.controlai.domain.auth.gateway.FindAppleLoginFlowByNonceDigestGateway
import br.com.nomar.controlai.domain.auth.gateway.ReadUnverifiedNonceClaimGateway
import br.com.nomar.controlai.domain.auth.gateway.SaveAppleLoginHandoffGateway
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant

// Runs when Apple posts the callback. The nonce claim in the identity token finds the flow, the token is then
// fully verified against that flow's nonce, and the browser is redirected to the app with a one-time handoff code.
@Component
class CompleteAppleLoginUseCase(
    private val readUnverifiedNonceClaimGateway: ReadUnverifiedNonceClaimGateway,
    private val findAppleLoginFlowByNonceDigestGateway: FindAppleLoginFlowByNonceDigestGateway,
    private val saveAppleLoginHandoffGateway: SaveAppleLoginHandoffGateway,
    private val appleLoginUseCase: AppleLoginUseCase,
    @Value("\${apple.return-uri.android}") private val androidReturnUri: String,
    @Value("\${app.web-url}") private val appWebUrl: String,
) {

    // Always returns a redirect target: the handoff code on success, or an error code. The user is never
    // left on a bare error page, and the error text never reaches the URL.
    fun execute(authorizationCode: String?, identityToken: String?, fullName: String?): String {
        val flow = identityToken?.let(::findFlowFor)
        val returnUri = returnUriFor(flow?.platform)

        return runCatching {
            val code = authorizationCode ?: throw InvalidAppleLoginFlowException()
            val token = identityToken ?: throw InvalidAppleLoginFlowException()
            val openFlow = flow ?: throw InvalidAppleLoginFlowException()
            val now = Instant.now()
            if (!openFlow.isOpenForCallback(now)) throw InvalidAppleLoginFlowException()

            val user = appleLoginUseCase.resolveUser(
                AppleLoginCommand(
                    identityToken = token,
                    authorizationCode = code,
                    nonce = openFlow.nonce,
                    fullName = fullName,
                ),
            ).getOrThrow()

            val handoffCode = OpaqueTokens.urlSafe()
            val saved = saveAppleLoginHandoffGateway.execute(
                openFlow.id,
                TokenHasher.sha256(handoffCode),
                user.id!!,
                now.plus(HANDOFF_TTL),
            ).getOrThrow()
            if (!saved) throw InvalidAppleLoginFlowException()

            handoffCode
        }.fold(
            onSuccess = { "$returnUri?code=$it" },
            onFailure = {
                logger.warn("Apple callback failed: {}", it::class.simpleName)
                "$returnUri?error=apple_login_failed"
            },
        )
    }

    private fun findFlowFor(identityToken: String): AppleLoginFlow? =
        readUnverifiedNonceClaimGateway.execute(identityToken).getOrNull()
            ?.let { findAppleLoginFlowByNonceDigestGateway.execute(it).getOrNull() }

    private fun returnUriFor(platform: AppleLoginPlatform?): String = when (platform) {
        AppleLoginPlatform.ANDROID -> androidReturnUri
        else -> "${appWebUrl.trimEnd('/')}/login"
    }

    companion object {
        private val HANDOFF_TTL: Duration = Duration.ofSeconds(60)
        private val logger = LoggerFactory.getLogger(CompleteAppleLoginUseCase::class.java)
    }
}
