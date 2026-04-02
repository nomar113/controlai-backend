package br.com.nomar.controlai.domain.payment_methods.usecase

import br.com.nomar.controlai.domain.payment_methods.gateway.DeactivateSubCardGateway
import org.springframework.stereotype.Component

@Component
class DeactivateSubCardUseCase(
    private val deactivateSubCardGateway: DeactivateSubCardGateway,
) {
    fun execute(id: Long): Result<Unit> {
        return runCatching {
            deactivateSubCardGateway.execute(id).getOrThrow()
        }
    }
}
