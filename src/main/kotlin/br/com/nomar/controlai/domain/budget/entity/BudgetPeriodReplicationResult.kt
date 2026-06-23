package br.com.nomar.controlai.domain.budget.entity

data class BudgetPeriodReplicationResult(
    val updated: List<Long>,
    val failed: List<FailedReplication>,
)

data class FailedReplication(
    val yearMonth: String,
    val reason: String,
)
