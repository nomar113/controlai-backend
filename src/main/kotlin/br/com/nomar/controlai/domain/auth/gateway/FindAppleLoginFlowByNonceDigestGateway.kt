package br.com.nomar.controlai.domain.auth.gateway

import br.com.nomar.controlai.domain.auth.entity.AppleLoginFlow

fun interface FindAppleLoginFlowByNonceDigestGateway {
    fun execute(nonceDigest: String): Result<AppleLoginFlow?>
}
