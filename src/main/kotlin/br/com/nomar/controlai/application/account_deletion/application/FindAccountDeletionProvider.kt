package br.com.nomar.controlai.application.account_deletion.application

import br.com.nomar.controlai.application.auth.entrypoint.database.repository.UserRepository
import br.com.nomar.controlai.domain.account_deletion.entity.AccountDeletion
import br.com.nomar.controlai.domain.account_deletion.gateway.FindAccountDeletionGateway
import org.springframework.stereotype.Component

@Component
class FindAccountDeletionProvider(
    private val userRepository: UserRepository,
) : FindAccountDeletionGateway {

    override fun execute(userId: Long): Result<AccountDeletion?> {
        return runCatching {
            val user = userRepository.findById(userId).orElseThrow()
            val requestedAt = user.deletionRequestedAt
            val scheduledFor = user.deletionScheduledFor
            if (requestedAt != null && scheduledFor != null) AccountDeletion(requestedAt, scheduledFor) else null
        }
    }
}
