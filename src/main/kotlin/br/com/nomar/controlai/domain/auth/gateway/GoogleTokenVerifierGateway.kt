package br.com.nomar.controlai.domain.auth.gateway

import br.com.nomar.controlai.domain.auth.entity.GoogleUserInfo

fun interface GoogleTokenVerifierGateway {
    fun verify(idToken: String): Result<GoogleUserInfo>
}
