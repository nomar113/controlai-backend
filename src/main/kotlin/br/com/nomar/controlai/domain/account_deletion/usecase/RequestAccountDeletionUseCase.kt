package br.com.nomar.controlai.domain.account_deletion.usecase

import br.com.nomar.controlai.domain.account_deletion.entity.AccountDeletion
import br.com.nomar.controlai.domain.account_deletion.exception.AccountDeletionAlreadyScheduledException
import br.com.nomar.controlai.domain.account_deletion.gateway.FindAccountDeletionGateway
import br.com.nomar.controlai.domain.account_deletion.gateway.ScheduleAccountDeletionGateway
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.time.temporal.ChronoUnit

@Component
class RequestAccountDeletionUseCase(
    private val findAccountDeletionGateway: FindAccountDeletionGateway,
    private val scheduleAccountDeletionGateway: ScheduleAccountDeletionGateway,
    private val meterRegistry: MeterRegistry,
    private val clock: Clock,
) {

    fun execute(userId: Long): Result<AccountDeletion> {
        return runCatching {
            if (findAccountDeletionGateway.execute(userId).getOrThrow() != null) {
                throw AccountDeletionAlreadyScheduledException()
            }
            // Whole seconds, as stored by the TIMESTAMP columns (MySQL rounds fractions): the
            // date returned here is then the same one GET /me/deletion reports later
            val requestedAt = clock.instant().truncatedTo(ChronoUnit.SECONDS)
            val deletion = AccountDeletion(requestedAt = requestedAt, scheduledFor = requestedAt.plus(GRACE_PERIOD))
            scheduleAccountDeletionGateway.execute(userId, deletion).getOrThrow()
            meterRegistry.counter("account.deletion.requested").increment()
            // Only the id: never log e-mail or name of someone asking to be forgotten
            logger.info("Account deletion requested for user {}, scheduled for {}", userId, deletion.scheduledFor)
            deletion
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(RequestAccountDeletionUseCase::class.java)

        val GRACE_PERIOD: Duration = Duration.ofDays(30)
    }
}
