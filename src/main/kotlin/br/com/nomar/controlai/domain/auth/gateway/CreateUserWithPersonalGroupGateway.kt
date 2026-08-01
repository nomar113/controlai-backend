package br.com.nomar.controlai.domain.auth.gateway

import br.com.nomar.controlai.domain.auth.entity.User

// Creates the user together with its personal group and membership in a single transaction
fun interface CreateUserWithPersonalGroupGateway {
    fun execute(user: User): Result<User>
}
