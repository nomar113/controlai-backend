package br.com.nomar.controlai.domain.auth.gateway

import br.com.nomar.controlai.domain.auth.entity.AppleIdentity

fun interface AppleIdentityVerifierGateway {
    fun verify(identityToken: String, expectedNonce: String?): Result<AppleIdentity>
}
