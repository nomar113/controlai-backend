package br.com.nomar.controlai.domain.auth.gateway

import br.com.nomar.controlai.domain.auth.entity.IdentityProvider
import br.com.nomar.controlai.domain.auth.entity.User

fun interface FindUserByIdentityGateway {
    fun execute(provider: IdentityProvider, subject: String): Result<User?>
}
