package br.com.nomar.controlai.application.account_deletion.application

import br.com.nomar.controlai.application.auth.entrypoint.database.repository.UserRepository
import br.com.nomar.controlai.domain.account_deletion.gateway.FindDueAccountDeletionsGateway
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class FindDueAccountDeletionsProvider(
    private val userRepository: UserRepository,
) : FindDueAccountDeletionsGateway {

    override fun execute(now: Instant): Result<List<Long>> =
        runCatching { userRepository.findIdsWithDeletionDueBy(now) }
}
