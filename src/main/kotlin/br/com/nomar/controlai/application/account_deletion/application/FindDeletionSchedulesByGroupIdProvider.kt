package br.com.nomar.controlai.application.account_deletion.application

import br.com.nomar.controlai.application.auth.entrypoint.database.repository.UserRepository
import br.com.nomar.controlai.domain.account_deletion.gateway.FindDeletionSchedulesByGroupIdGateway
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class FindDeletionSchedulesByGroupIdProvider(
    private val userRepository: UserRepository,
) : FindDeletionSchedulesByGroupIdGateway {

    override fun execute(groupId: Long): Result<List<Instant?>> =
        runCatching { userRepository.findDeletionScheduledForByGroupId(groupId) }
}
