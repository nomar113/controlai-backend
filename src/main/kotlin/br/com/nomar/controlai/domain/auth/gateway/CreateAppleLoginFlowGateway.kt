package br.com.nomar.controlai.domain.auth.gateway

import br.com.nomar.controlai.domain.auth.entity.AppleLoginPlatform
import java.time.Instant

fun interface CreateAppleLoginFlowGateway {
    fun execute(nonce: String, platform: AppleLoginPlatform, expiresAt: Instant): Result<Unit>
}
