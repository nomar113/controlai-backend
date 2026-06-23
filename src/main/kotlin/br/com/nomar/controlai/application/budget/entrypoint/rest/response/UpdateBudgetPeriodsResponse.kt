package br.com.nomar.controlai.application.budget.entrypoint.rest.response

import br.com.nomar.controlai.domain.budget.entity.BudgetPeriodReplicationResult
import br.com.nomar.controlai.domain.budget.entity.FailedReplication

data class UpdateBudgetPeriodsResponse(
    val updated: List<Long>,
    val failed: List<FailedReplicationResponse>,
) {
    companion object {
        fun from(entity: BudgetPeriodReplicationResult) = UpdateBudgetPeriodsResponse(
            updated = entity.updated,
            failed = entity.failed.map(FailedReplicationResponse::from),
        )
    }
}

data class FailedReplicationResponse(
    val yearMonth: String,
    val reason: String,
) {
    companion object {
        fun from(entity: FailedReplication) = FailedReplicationResponse(
            yearMonth = entity.yearMonth,
            reason = entity.reason,
        )
    }
}
