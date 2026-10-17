package br.com.nomar.controlai.domain.auth.usecase

import br.com.nomar.controlai.domain.auth.OpaqueTokens
import br.com.nomar.controlai.domain.auth.TokenHasher
import br.com.nomar.controlai.domain.auth.entity.AppleCallback
import br.com.nomar.controlai.domain.auth.entity.AppleLoginCommand
import br.com.nomar.controlai.domain.auth.entity.AppleLoginFlow
import br.com.nomar.controlai.domain.auth.entity.AppleLoginHandoff
import br.com.nomar.controlai.domain.auth.entity.AppleLoginPlatform
import br.com.nomar.controlai.domain.auth.exception.InvalidAppleLoginFlowException
import br.com.nomar.controlai.domain.auth.gateway.FindAppleLoginFlowByNonceDigestGateway
import br.com.nomar.controlai.domain.auth.gateway.FindAppleLoginFlowByStateGateway
import br.com.nomar.controlai.domain.auth.gateway.ReadUnverifiedNonceClaimGateway
import br.com.nomar.controlai.domain.auth.gateway.SaveAppleLoginHandoffGateway
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant

// Runs when Apple posts the callback. The nonce claim in the identity token finds the flow; the state Apple
// returns must match the one stored for that flow; the token is then verified against the flow's nonce, and the
// browser is redirected to the app with a one-time handoff code and the state, so the app can check it too.
@Component
class CompleteAppleLoginUseCase(
    private val readUnverifiedNonceClaimGateway: ReadUnverifiedNonceClaimGateway,
    private val findAppleLoginFlowByNonceDigestGateway: FindAppleLoginFlowByNonceDigestGateway,
    private val findAppleLoginFlowByStateGateway: FindAppleLoginFlowByStateGateway,
    private val saveAppleLoginHandoffGateway: SaveAppleLoginHandoffGateway,
    private val appleLoginUseCase: AppleLoginUseCase,
    @Value("\${apple.return-uri.android}") private val androidReturnUri: String,
    @Value("\${app.web-url}") private val appWebUrl: String,
) {

    // Always returns a redirect target. On success it carries the handoff code and the state; on failure only an
    // error code, plus the state stored for the flow when it matches what Apple returned.
    fun execute(callback: AppleCallback): String {
        val flow = callback.identityToken?.let(::findFlowFor)
        // Apple sends no identity token when the user cancels, so the state is the only way to know the platform
        val platformFlow = flow ?: callback.state?.let(::findFlowByState)
        val returnUri = returnUriFor(platformFlow?.platform)

        return runCatching {
            val code = callback.authorizationCode ?: throw InvalidAppleLoginFlowException()
            val token = callback.identityToken ?: throw InvalidAppleLoginFlowException()
            val openFlow = flow ?: throw InvalidAppleLoginFlowException()
            val now = Instant.now()
            if (!openFlow.isOpenForCallback(now)) throw InvalidAppleLoginFlowException()
            if (!stateMatches(openFlow, callback.state)) throw InvalidAppleLoginFlowException()

            val user = appleLoginUseCase.resolveUser(
                AppleLoginCommand(
                    identityToken = token,
                    authorizationCode = code,
                    nonce = openFlow.nonce,
                    fullName = callback.fullName,
                ),
            ).getOrThrow()

            val handoffCode = OpaqueTokens.urlSafe()
            val saved = saveAppleLoginHandoffGateway.execute(
                AppleLoginHandoff(
                    flowId = openFlow.id,
                    now = now,
                    codeHash = TokenHasher.sha256(handoffCode),
                    userId = user.id!!,
                    expiresAt = now.plus(HANDOFF_TTL),
                ),
            ).getOrThrow()
            if (!saved) throw InvalidAppleLoginFlowException()

            "$returnUri?code=$handoffCode&state=${openFlow.state}"
        }.getOrElse {
            logger.warn("Apple callback failed: {}", it::class.simpleName)
            // Only the state stored on the server is echoed, and only when it matches what came back. Nothing
            // sent by the caller reaches the redirect URL.
            val echoedState = platformFlow?.state?.takeIf { it == callback.state }
            if (echoedState == null) "$returnUri?error=apple_login_failed"
            else "$returnUri?error=apple_login_failed&state=$echoedState"
        }
    }

    private fun findFlowFor(identityToken: String): AppleLoginFlow? =
        readUnverifiedNonceClaimGateway.execute(identityToken).getOrNull()
            ?.let { findAppleLoginFlowByNonceDigestGateway.execute(it).getOrNull() }

    private fun findFlowByState(state: String): AppleLoginFlow? =
        findAppleLoginFlowByStateGateway.execute(state).getOrNull()

    // The state is a fixed-length hex value, so comparing lengths first reveals nothing secret
    private fun stateMatches(flow: AppleLoginFlow, returnedState: String?): Boolean {
        val expected = flow.state ?: return false
        val returned = returnedState ?: return false
        return MessageDigest.isEqual(expected.toByteArray(), returned.toByteArray())
    }

    private fun returnUriFor(platform: AppleLoginPlatform?): String = when (platform) {
        AppleLoginPlatform.ANDROID -> androidReturnUri
        else -> "${appWebUrl.trimEnd('/')}/login"
    }

    companion object {
        private val HANDOFF_TTL: Duration = Duration.ofSeconds(60)
        private val logger = LoggerFactory.getLogger(CompleteAppleLoginUseCase::class.java)
    }
}
