package br.com.nomar.controlai.domain.auth.gateway

import br.com.nomar.controlai.domain.auth.entity.AppleLoginFlow

// Only used to pick the app a failed or cancelled login returns to. It never decides whether a login succeeds.
fun interface FindAppleLoginFlowByStateGateway {
    fun execute(state: String): Result<AppleLoginFlow?>
}
