package br.com.nomar.controlai.domain.payment_methods.usecase

import br.com.nomar.controlai.domain.payment_methods.entity.PaymentMethod
import br.com.nomar.controlai.domain.payment_methods.gateway.ListPaymentMethodsGateway
import org.springframework.stereotype.Component

@Component
class ListPaymentMethodsUseCase(
    private val listPaymentMethodsGateway: ListPaymentMethodsGateway,
) {
    fun execute(holderId: Long? = null): Result<List<PaymentMethod>> {
        return runCatching {
            listPaymentMethodsGateway.execute(holderId).getOrThrow()
        }
    }
}
