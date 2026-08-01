package br.com.nomar.controlai.domain.auth.gateway

import br.com.nomar.controlai.domain.auth.entity.User

fun interface FindUserByEmailGateway {
    fun execute(email: String): Result<User?>
}
