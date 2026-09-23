package br.com.nomar.controlai.application.account_deletion.entrypoint.scheduler

import br.com.nomar.controlai.domain.account_deletion.usecase.PurgeDueAccountsUseCase
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

// Daily job that physically deletes the accounts whose 30-day grace period is over.
// Turned off by account-deletion.purge-enabled=false (the test suite does so).
@Component
@ConditionalOnProperty(name = ["account-deletion.purge-enabled"], havingValue = "true", matchIfMissing = true)
class AccountPurgeScheduler(
    private val purgeDueAccountsUseCase: PurgeDueAccountsUseCase,
) {

    @Scheduled(cron = "\${account-deletion.purge-cron:0 0 4 * * *}", zone = "America/Sao_Paulo")
    fun purgeDueAccounts() {
        // Failures of a single account are handled inside the use case; this only fires when
        // the due accounts could not even be listed
        purgeDueAccountsUseCase.execute()
            .onFailure { logger.error("Account purge job failed before purging any account", it) }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(AccountPurgeScheduler::class.java)
    }
}
