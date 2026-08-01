package br.com.nomar.controlai.application.auth.application

import br.com.nomar.controlai.domain.auth.entity.AuthSession
import br.com.nomar.controlai.domain.auth.entity.User
import br.com.nomar.controlai.domain.auth.gateway.IssueTokensGateway
import org.springframework.stereotype.Component

@Component
class IssueTokensProvider(
    private val tokenIssuer: TokenIssuer,
) : IssueTokensGateway {

    override fun execute(user: User): Result<AuthSession> {
        return runCatching {
            tokenIssuer.issueSession(user).session
        }
    }
}
