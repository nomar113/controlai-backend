package br.com.nomar.controlai.domain.payment_methods.usecase

import br.com.nomar.controlai.domain.payment_methods.entity.PaymentMethod
import br.com.nomar.controlai.domain.payment_methods.gateway.UpdatePaymentMethodGateway
import org.springframework.stereotype.Component

@Component
class UpdatePaymentMethodUseCase(
    private val updatePaymentMethodGateway: UpdatePaymentMethodGateway,
) {
    fun execute(paymentMethod: PaymentMethod): Result<PaymentMethod> {
        return runCatching {
            updatePaymentMethodGateway.execute(paymentMethod).getOrThrow()
        }
    }
}
