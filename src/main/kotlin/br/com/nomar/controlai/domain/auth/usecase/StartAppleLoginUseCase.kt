package br.com.nomar.controlai.domain.auth.usecase

import br.com.nomar.controlai.domain.auth.OpaqueTokens
import br.com.nomar.controlai.domain.auth.entity.AppleLoginPlatform
import br.com.nomar.controlai.domain.auth.gateway.CreateAppleLoginFlowGateway
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant

// Before opening Apple's page the app asks for a nonce and a state. Both are stored here: the nonce is
// checked against the identity token, and the state against the callback and the redirect back to the app.
@Component
class StartAppleLoginUseCase(
    private val createAppleLoginFlowGateway: CreateAppleLoginFlowGateway,
) {

    fun execute(platform: AppleLoginPlatform): Result<AppleLoginStart> {
        return runCatching {
            val start = AppleLoginStart(nonce = OpaqueTokens.hex(), state = OpaqueTokens.hex())
            createAppleLoginFlowGateway.execute(start.nonce, start.state, platform, Instant.now().plus(FLOW_TTL))
                .getOrThrow()
            start
        }
    }

    companion object {
        private val FLOW_TTL: Duration = Duration.ofMinutes(10)
    }
}

data class AppleLoginStart(val nonce: String, val state: String)
