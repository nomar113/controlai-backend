package br.com.nomar.controlai.domain.account_deletion.usecase

import br.com.nomar.controlai.domain.account_deletion.entity.PurgeOutcome
import br.com.nomar.controlai.domain.account_deletion.entity.PurgeSummary
import br.com.nomar.controlai.domain.account_deletion.gateway.FindDueAccountDeletionsGateway
import br.com.nomar.controlai.domain.account_deletion.gateway.PurgeAccountGateway
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration

@Component
class PurgeDueAccountsUseCase(
    private val findDueAccountDeletionsGateway: FindDueAccountDeletionsGateway,
    private val purgeAccountGateway: PurgeAccountGateway,
    private val meterRegistry: MeterRegistry,
    private val clock: Clock,
) {

    fun execute(): Result<PurgeSummary> {
        return runCatching {
            val startedAt = clock.instant()
            val dueUserIds = findDueAccountDeletionsGateway.execute(startedAt).getOrThrow()
            // Each account is purged in its own transaction: a failure is recorded and the
            // loop moves on, so one broken account never blocks the others
            val outcomes = dueUserIds.map { userId -> purge(userId) }
            val summary = PurgeSummary(
                due = dueUserIds.size,
                purged = outcomes.count { it in PURGED_OUTCOMES },
                skipped = outcomes.count { it == PurgeOutcome.NO_LONGER_DUE },
                failed = outcomes.count { it == null },
            )
            logger.info(
                "Account purge finished: {} due, {} purged, {} skipped, {} failed in {} ms",
                summary.due,
                summary.purged,
                summary.skipped,
                summary.failed,
                Duration.between(startedAt, clock.instant()).toMillis(),
            )
            summary
        }
    }

    // Null when the purge failed
    private fun purge(userId: Long): PurgeOutcome? {
        return purgeAccountGateway.execute(userId)
            .onSuccess { outcome -> recordOutcome(userId, outcome) }
            .onFailure { error ->
                meterRegistry.counter("account.deletion.purge.failure").increment()
                // Only the id: never log e-mail or name of someone asking to be forgotten
                logger.error("Account purge failed for user {}", userId, error)
            }
            .getOrNull()
    }

    private fun recordOutcome(userId: Long, outcome: PurgeOutcome) {
        val tag = when (outcome) {
            PurgeOutcome.SOLE_MEMBER_GROUP_PURGED -> "sole_member"
            PurgeOutcome.MEMBER_REMOVED -> "member_removed"
            PurgeOutcome.NO_LONGER_DUE -> {
                logger.info("Account purge skipped for user {}: deletion no longer due", userId)
                return
            }
        }
        meterRegistry.counter("account.deletion.purged", "outcome", tag).increment()
        // The ids purged are what the runbook checks after restoring a backup, so that a
        // deleted account is never brought back
        logger.info("Account purged for user {} ({})", userId, tag)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(PurgeDueAccountsUseCase::class.java)

        private val PURGED_OUTCOMES = setOf(PurgeOutcome.SOLE_MEMBER_GROUP_PURGED, PurgeOutcome.MEMBER_REMOVED)
    }
}
