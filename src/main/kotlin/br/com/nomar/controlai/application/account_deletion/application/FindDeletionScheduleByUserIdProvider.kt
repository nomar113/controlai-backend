package br.com.nomar.controlai.application.account_deletion.application

import br.com.nomar.controlai.application.auth.entrypoint.database.repository.UserRepository
import br.com.nomar.controlai.domain.account_deletion.gateway.FindDeletionScheduleByUserIdGateway
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class FindDeletionScheduleByUserIdProvider(
    private val userRepository: UserRepository,
) : FindDeletionScheduleByUserIdGateway {

    override fun execute(userId: Long): Result<Instant?> =
        runCatching { userRepository.findDeletionScheduledForById(userId) }
}
