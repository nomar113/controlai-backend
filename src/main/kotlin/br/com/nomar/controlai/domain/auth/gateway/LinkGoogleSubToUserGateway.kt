package br.com.nomar.controlai.domain.auth.gateway

import br.com.nomar.controlai.domain.auth.entity.User

fun interface LinkGoogleSubToUserGateway {
    fun execute(userId: Long, googleSub: String): Result<User>
}
