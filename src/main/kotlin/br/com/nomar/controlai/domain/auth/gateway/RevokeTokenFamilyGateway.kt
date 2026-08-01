package br.com.nomar.controlai.domain.auth.gateway

import br.com.nomar.controlai.domain.auth.entity.RefreshToken

// Revokes the given token and every descendant produced by rotation (theft detection)
fun interface RevokeTokenFamilyGateway {
    fun execute(token: RefreshToken): Result<Int>
}
