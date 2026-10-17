package br.com.nomar.controlai.domain.auth.gateway

import br.com.nomar.controlai.domain.auth.entity.AppleLoginHandoff

// Returns false when the nonce was already used, so a replayed callback never gets a second handoff code
fun interface SaveAppleLoginHandoffGateway {
    fun execute(handoff: AppleLoginHandoff): Result<Boolean>
}
