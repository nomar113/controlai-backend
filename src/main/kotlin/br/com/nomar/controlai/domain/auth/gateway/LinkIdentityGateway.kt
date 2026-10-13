package br.com.nomar.controlai.domain.auth.gateway

import br.com.nomar.controlai.domain.auth.entity.UserIdentity

fun interface LinkIdentityGateway {
    fun execute(identity: UserIdentity): Result<Unit>
}
