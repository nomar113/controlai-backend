package br.com.nomar.controlai.domain.account_deletion.usecase

import br.com.nomar.controlai.domain.account_deletion.gateway.FindDeletionScheduleByUserIdGateway
import org.springframework.stereotype.Component
import java.time.Instant

// Tells whether a user's requests must be blocked during the deletion grace period. A non-null
// result is the deletion date to report to the client.
@Component
class ResolveUserDeletionBlockUseCase(
    private val findDeletionScheduleByUserIdGateway: FindDeletionScheduleByUserIdGateway,
) {

    fun execute(userId: Long): Result<Instant?> = findDeletionScheduleByUserIdGateway.execute(userId)
}
