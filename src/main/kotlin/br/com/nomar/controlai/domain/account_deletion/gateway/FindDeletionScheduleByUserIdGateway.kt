package br.com.nomar.controlai.domain.account_deletion.gateway

import java.time.Instant

// Date the user's account will be deleted, or null when no deletion is scheduled (or the user
// no longer exists)
fun interface FindDeletionScheduleByUserIdGateway {
    fun execute(userId: Long): Result<Instant?>
}
