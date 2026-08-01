package br.com.nomar.controlai.domain.auth.gateway

import br.com.nomar.controlai.domain.auth.entity.AuthSession
import br.com.nomar.controlai.domain.auth.entity.User

fun interface IssueTokensGateway {
    fun execute(user: User): Result<AuthSession>
}
