package br.com.nomar.controlai.domain.account_deletion.gateway

import java.time.Instant

// Ids of the users whose deletion date is at or before `now`
fun interface FindDueAccountDeletionsGateway {
    fun execute(now: Instant): Result<List<Long>>
}
