package br.com.nomar.controlai.domain.auth.usecase

import br.com.nomar.controlai.domain.auth.OpaqueTokens
import br.com.nomar.controlai.domain.auth.entity.AppleLoginPlatform
import br.com.nomar.controlai.domain.auth.gateway.CreateAppleLoginFlowGateway
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant

// The app asks for a nonce before opening Apple's sheet; the nonce is stored here and checked on the callback
@Component
class StartAppleLoginUseCase(
    private val createAppleLoginFlowGateway: CreateAppleLoginFlowGateway,
) {

    fun execute(platform: AppleLoginPlatform): Result<String> {
        return runCatching {
            val nonce = OpaqueTokens.hex()
            createAppleLoginFlowGateway.execute(nonce, platform, Instant.now().plus(NONCE_TTL)).getOrThrow()
            nonce
        }
    }

    companion object {
        private val NONCE_TTL: Duration = Duration.ofMinutes(10)
    }
}
