package br.com.nomar.controlai.domain.auth.gateway

import br.com.nomar.controlai.domain.auth.entity.PasswordResetToken

fun interface CreatePasswordResetTokenGateway {
    fun execute(token: PasswordResetToken): Result<PasswordResetToken>
}
