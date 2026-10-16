package br.com.nomar.controlai.domain.auth.usecase

import br.com.nomar.controlai.domain.auth.TokenHasher
import br.com.nomar.controlai.domain.auth.entity.AuthSession
import br.com.nomar.controlai.domain.auth.exception.InvalidAppleLoginFlowException
import br.com.nomar.controlai.domain.auth.gateway.ConsumeAppleLoginHandoffGateway
import br.com.nomar.controlai.domain.auth.gateway.FindAppleLoginFlowByHandoffCodeHashGateway
import br.com.nomar.controlai.domain.auth.gateway.FindUserByIdGateway
import br.com.nomar.controlai.domain.auth.gateway.IssueTokensGateway
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Instant

// The app trades the one-time handoff code for a session. The code works once and only within its short TTL.
@Component
class ExchangeAppleLoginCodeUseCase(
    private val findAppleLoginFlowByHandoffCodeHashGateway: FindAppleLoginFlowByHandoffCodeHashGateway,
    private val consumeAppleLoginHandoffGateway: ConsumeAppleLoginHandoffGateway,
    private val findUserByIdGateway: FindUserByIdGateway,
    private val issueTokensGateway: IssueTokensGateway,
) {

    fun execute(handoffCode: String): Result<AuthSession> {
        return runCatching {
            val flow = findAppleLoginFlowByHandoffCodeHashGateway.execute(TokenHasher.sha256(handoffCode))
                .getOrThrow() ?: throw InvalidAppleLoginFlowException()
            if (!flow.isHandoffUsable(Instant.now())) throw InvalidAppleLoginFlowException()
            val userId = flow.userId ?: throw InvalidAppleLoginFlowException()

            if (!consumeAppleLoginHandoffGateway.execute(flow.id).getOrThrow()) throw InvalidAppleLoginFlowException()
            val user = findUserByIdGateway.execute(userId).getOrThrow() ?: throw InvalidAppleLoginFlowException()
            issueTokensGateway.execute(user).getOrThrow()
        }.onFailure { logger.warn("Apple handoff exchange failed: {}", it::class.simpleName) }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ExchangeAppleLoginCodeUseCase::class.java)
    }
}
