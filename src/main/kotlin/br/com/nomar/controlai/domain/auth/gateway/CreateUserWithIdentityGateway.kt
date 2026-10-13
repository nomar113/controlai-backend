package br.com.nomar.controlai.domain.auth.gateway

import br.com.nomar.controlai.domain.auth.entity.IdentityProvider
import br.com.nomar.controlai.domain.auth.entity.User

// Creates the user, its personal group and its external identity in a single transaction
fun interface CreateUserWithIdentityGateway {
    fun execute(user: User, provider: IdentityProvider, subject: String): Result<User>
}
