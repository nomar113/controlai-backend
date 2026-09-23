package br.com.nomar.controlai.application.account_deletion.application

import br.com.nomar.controlai.application.auth.entrypoint.database.repository.RefreshTokenRepository
import br.com.nomar.controlai.application.auth.entrypoint.database.repository.UserRepository
import br.com.nomar.controlai.application.groups.entrypoint.database.model.GroupInviteStatusModel
import br.com.nomar.controlai.application.groups.entrypoint.database.repository.GroupInviteRepository
import br.com.nomar.controlai.domain.account_deletion.entity.AccountDeletion
import br.com.nomar.controlai.domain.account_deletion.exception.AccountDeletionAlreadyScheduledException
import br.com.nomar.controlai.domain.account_deletion.gateway.ScheduleAccountDeletionGateway
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate

@Component
class ScheduleAccountDeletionProvider(
    private val userRepository: UserRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val groupInviteRepository: GroupInviteRepository,
    private val transactionTemplate: TransactionTemplate,
) : ScheduleAccountDeletionGateway {

    // TransactionTemplate instead of @Transactional: runCatching would swallow the
    // exception before the proxy could mark the transaction for rollback
    override fun execute(userId: Long, deletion: AccountDeletion): Result<Unit> {
        return runCatching {
            transactionTemplate.executeWithoutResult {
                val user = userRepository.findById(userId).orElseThrow()
                // A concurrent request may have scheduled it after the use case checked; throwing
                // here rolls back, so sessions and invites are only touched by the winning request
                if (userRepository.scheduleDeletion(userId, deletion.requestedAt, deletion.scheduledFor) == 0) {
                    throw AccountDeletionAlreadyScheduledException()
                }
                refreshTokenRepository.revokeAllActiveByUserId(userId, deletion.requestedAt)
                groupInviteRepository.updateStatusSentOrReceivedBy(
                    userId = userId,
                    email = user.email,
                    from = GroupInviteStatusModel.PENDING,
                    to = GroupInviteStatusModel.CANCELLED,
                )
            }
        }
    }
}
