package br.com.nomar.controlai.domain.auth.gateway

import java.time.Instant

// Returns false when the nonce was already used, so a replayed callback never gets a second handoff code
fun interface SaveAppleLoginHandoffGateway {
    fun execute(flowId: Long, handoffCodeHash: String, userId: Long, handoffExpiresAt: Instant): Result<Boolean>
}
