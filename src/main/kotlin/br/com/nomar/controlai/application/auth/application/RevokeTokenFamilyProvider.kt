package br.com.nomar.controlai.application.auth.application

import br.com.nomar.controlai.application.auth.entrypoint.database.repository.RefreshTokenRepository
import br.com.nomar.controlai.domain.auth.entity.RefreshToken
import br.com.nomar.controlai.domain.auth.gateway.RevokeTokenFamilyGateway
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant

@Component
class RevokeTokenFamilyProvider(
    private val refreshTokenRepository: RefreshTokenRepository,
    private val transactionTemplate: TransactionTemplate,
) : RevokeTokenFamilyGateway {

    // Walks the rotation chain forward from the presented token, revoking every descendant
    // (including the currently live one), so a stolen rotated token kills the whole session
    override fun execute(token: RefreshToken): Result<Int> {
        return runCatching {
            transactionTemplate.execute {
                var revokedCount = 0
                var current = token.id?.let { refreshTokenRepository.findById(it).orElse(null) }
                var hops = 0
                while (current != null && hops < MAX_FAMILY_SIZE) {
                    if (current.revokedAt == null) {
                        current.revokedAt = Instant.now()
                        refreshTokenRepository.save(current)
                        revokedCount++
                    }
                    current = current.replacedById?.let { refreshTokenRepository.findById(it).orElse(null) }
                    hops++
                }
                revokedCount
            }!!
        }
    }

    companion object {
        // Safety bound for the chain walk; families never grow anywhere near this size
        private const val MAX_FAMILY_SIZE = 10_000
    }
}
