package br.com.nomar.controlai.domain.account_deletion.usecase

import br.com.nomar.controlai.domain.account_deletion.entity.AccountDeletion
import br.com.nomar.controlai.domain.account_deletion.gateway.FindAccountDeletionGateway
import org.springframework.stereotype.Component

@Component
class GetAccountDeletionUseCase(
    private val findAccountDeletionGateway: FindAccountDeletionGateway,
) {

    // null means no deletion is scheduled for the user
    fun execute(userId: Long): Result<AccountDeletion?> = findAccountDeletionGateway.execute(userId)
}
