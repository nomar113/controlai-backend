package br.com.nomar.controlai.domain.account_deletion.gateway

import br.com.nomar.controlai.domain.account_deletion.entity.AccountDeletion

fun interface FindAccountDeletionGateway {
    fun execute(userId: Long): Result<AccountDeletion?>
}
