package br.com.nomar.controlai.domain.account_deletion.entity

data class PurgeSummary(
    val due: Int,
    val purged: Int,
    val skipped: Int,
    val failed: Int,
)
