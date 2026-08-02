package br.com.nomar.controlai.application.auth.application

import br.com.nomar.controlai.application.auth.entrypoint.database.repository.PasswordResetTokenRepository
import br.com.nomar.controlai.domain.auth.gateway.MarkPasswordResetTokenUsedGateway
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class MarkPasswordResetTokenUsedProvider(
    private val passwordResetTokenRepository: PasswordResetTokenRepository,
) : MarkPasswordResetTokenUsedGateway {

    override fun execute(tokenId: Long): Result<Unit> {
        return runCatching {
            passwordResetTokenRepository.markUsedByIdIfNotUsed(tokenId, Instant.now())
        }
    }
}
