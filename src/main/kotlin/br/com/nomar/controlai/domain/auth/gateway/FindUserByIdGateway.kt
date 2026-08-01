package br.com.nomar.controlai.domain.auth.gateway

import br.com.nomar.controlai.domain.auth.entity.User

fun interface FindUserByIdGateway {
    fun execute(id: Long): Result<User?>
}
