package br.com.nomar.controlai.domain.account_deletion.gateway

import br.com.nomar.controlai.domain.account_deletion.entity.PurgeOutcome

// Physically deletes one account whose deletion is due, in a transaction of its own
fun interface PurgeAccountGateway {
    fun execute(userId: Long): Result<PurgeOutcome>
}
