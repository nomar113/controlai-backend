package br.com.nomar.controlai.domain.payment_methods.usecase

import br.com.nomar.controlai.domain.payment_methods.entity.PaymentMethod
import br.com.nomar.controlai.domain.payment_methods.gateway.SavePaymentMethodGateway
import org.springframework.stereotype.Component

@Component
class SavePaymentMethodUseCase(
    private val savePaymentMethodGateway: SavePaymentMethodGateway,
) {
    fun execute(paymentMethod: PaymentMethod): Result<PaymentMethod> {
        return runCatching {
            savePaymentMethodGateway.execute(paymentMethod).getOrThrow()
        }
    }
}
