package br.com.nomar.controlai.domain.account_deletion.gateway

import br.com.nomar.controlai.domain.account_deletion.entity.AccountDeletion

// Stores the schedule, revokes every session of the user and cancels their pending invites,
// all in one transaction
fun interface ScheduleAccountDeletionGateway {
    fun execute(userId: Long, deletion: AccountDeletion): Result<Unit>
}
