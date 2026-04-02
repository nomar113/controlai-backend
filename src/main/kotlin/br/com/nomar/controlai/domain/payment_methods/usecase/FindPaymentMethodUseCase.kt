package br.com.nomar.controlai.domain.payment_methods.usecase

import br.com.nomar.controlai.domain.payment_methods.entity.PaymentMethod
import br.com.nomar.controlai.domain.payment_methods.gateway.FindPaymentMethodGateway
import org.springframework.stereotype.Component

@Component
class FindPaymentMethodUseCase(
    private val findPaymentMethodGateway: FindPaymentMethodGateway,
) {
    fun execute(id: Long): Result<PaymentMethod> {
        return runCatching {
            findPaymentMethodGateway.execute(id).getOrThrow()
        }
    }
}
