package br.com.nomar.controlai.domain.auth.gateway

import br.com.nomar.controlai.domain.auth.entity.User

fun interface FindUserByGoogleSubGateway {
    fun execute(googleSub: String): Result<User?>
}
