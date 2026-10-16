package br.com.nomar.controlai.domain.auth.gateway

import br.com.nomar.controlai.domain.auth.entity.AppleLoginFlow

fun interface FindAppleLoginFlowByHandoffCodeHashGateway {
    fun execute(handoffCodeHash: String): Result<AppleLoginFlow?>
}
