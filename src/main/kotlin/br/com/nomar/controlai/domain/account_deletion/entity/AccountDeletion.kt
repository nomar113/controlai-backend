package br.com.nomar.controlai.domain.account_deletion.entity

import java.time.Instant

data class AccountDeletion(
    val requestedAt: Instant,
    val scheduledFor: Instant,
)
