package br.com.nomar.controlai.domain.auth.gateway

import br.com.nomar.controlai.domain.auth.entity.AuthSession
import br.com.nomar.controlai.domain.auth.entity.RefreshToken
import br.com.nomar.controlai.domain.auth.entity.User

// Issues a new token pair and marks the old refresh token as replaced, atomically
fun interface RotateRefreshTokenGateway {
    fun execute(oldToken: RefreshToken, user: User): Result<AuthSession>
}
