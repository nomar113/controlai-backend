package br.com.nomar.controlai.application.auth.application

import br.com.nomar.controlai.application.auth.entrypoint.database.repository.RefreshTokenRepository
import br.com.nomar.controlai.domain.auth.entity.AuthSession
import br.com.nomar.controlai.domain.auth.entity.RefreshToken
import br.com.nomar.controlai.domain.auth.entity.User
import br.com.nomar.controlai.domain.auth.gateway.RotateRefreshTokenGateway
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant

@Component
class RotateRefreshTokenProvider(
    private val tokenIssuer: TokenIssuer,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val transactionTemplate: TransactionTemplate,
) : RotateRefreshTokenGateway {

    // TransactionTemplate instead of @Transactional: runCatching would swallow the
    // exception before the proxy could mark the transaction for rollback
    override fun execute(oldToken: RefreshToken, user: User): Result<AuthSession> {
        return runCatching {
            transactionTemplate.execute {
                val issued = tokenIssuer.issueSession(user, oldToken.absoluteExpiresAt)
                val oldModel = refreshTokenRepository.findById(requireNotNull(oldToken.id)).orElseThrow()
                oldModel.revokedAt = Instant.now()
                oldModel.replacedById = issued.refreshTokenId
                refreshTokenRepository.save(oldModel)
                issued.session
            }!!
        }
    }
}
