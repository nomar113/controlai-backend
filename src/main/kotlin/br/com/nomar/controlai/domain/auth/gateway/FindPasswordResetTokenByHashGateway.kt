package br.com.nomar.controlai.domain.auth.gateway

import br.com.nomar.controlai.domain.auth.entity.PasswordResetToken

fun interface FindPasswordResetTokenByHashGateway {
    fun execute(tokenHash: String): Result<PasswordResetToken?>
}
