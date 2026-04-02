package br.com.nomar.controlai.domain.payment_methods.usecase

import br.com.nomar.controlai.domain.payment_methods.gateway.DeactivatePaymentMethodGateway
import org.springframework.stereotype.Component

@Component
class DeactivatePaymentMethodUseCase(
    private val deactivatePaymentMethodGateway: DeactivatePaymentMethodGateway,
) {
    fun execute(id: Long): Result<Unit> {
        return runCatching {
            deactivatePaymentMethodGateway.execute(id).getOrThrow()
        }
    }
}
