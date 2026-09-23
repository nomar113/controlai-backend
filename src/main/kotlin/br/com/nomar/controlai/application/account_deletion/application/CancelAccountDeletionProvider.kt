package br.com.nomar.controlai.application.account_deletion.application

import br.com.nomar.controlai.application.auth.entrypoint.database.repository.UserRepository
import br.com.nomar.controlai.domain.account_deletion.exception.AccountDeletionNotScheduledException
import br.com.nomar.controlai.domain.account_deletion.gateway.CancelAccountDeletionGateway
import org.springframework.stereotype.Component

@Component
class CancelAccountDeletionProvider(
    private val userRepository: UserRepository,
) : CancelAccountDeletionGateway {

    override fun execute(userId: Long): Result<Unit> {
        return runCatching {
            // Covers a concurrent cancel that won after the use case checked
            if (userRepository.clearDeletion(userId) == 0) {
                throw AccountDeletionNotScheduledException()
            }
        }
    }
}
