package br.com.nomar.controlai.domain.account_deletion.usecase

import br.com.nomar.controlai.domain.account_deletion.exception.AccountDeletionNotScheduledException
import br.com.nomar.controlai.domain.account_deletion.gateway.CancelAccountDeletionGateway
import br.com.nomar.controlai.domain.account_deletion.gateway.FindAccountDeletionGateway
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class CancelAccountDeletionUseCase(
    private val findAccountDeletionGateway: FindAccountDeletionGateway,
    private val cancelAccountDeletionGateway: CancelAccountDeletionGateway,
    private val meterRegistry: MeterRegistry,
) {

    fun execute(userId: Long): Result<Unit> {
        return runCatching {
            findAccountDeletionGateway.execute(userId).getOrThrow()
                ?: throw AccountDeletionNotScheduledException()
            cancelAccountDeletionGateway.execute(userId).getOrThrow()
            meterRegistry.counter("account.deletion.cancelled").increment()
            logger.info("Account deletion cancelled for user {}", userId)
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(CancelAccountDeletionUseCase::class.java)
    }
}
