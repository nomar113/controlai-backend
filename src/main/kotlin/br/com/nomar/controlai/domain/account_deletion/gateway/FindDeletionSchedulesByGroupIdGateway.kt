package br.com.nomar.controlai.domain.account_deletion.gateway

import java.time.Instant

// One entry per member of the group: the date their account will be deleted, or null when
// that member has no deletion scheduled
fun interface FindDeletionSchedulesByGroupIdGateway {
    fun execute(groupId: Long): Result<List<Instant?>>
}
