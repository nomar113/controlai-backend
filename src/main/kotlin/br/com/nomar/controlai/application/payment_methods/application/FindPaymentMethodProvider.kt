package br.com.nomar.controlai.application.payment_methods.application

import br.com.nomar.controlai.application.payment_methods.converter.PaymentMethodConverter
import br.com.nomar.controlai.application.payment_methods.entrypoint.database.repository.PaymentMethodRepository
import br.com.nomar.controlai.domain.payment_methods.entity.PaymentMethod
import br.com.nomar.controlai.domain.payment_methods.gateway.FindPaymentMethodGateway
import org.springframework.stereotype.Component

@Component
class FindPaymentMethodProvider(
    private val paymentMethodRepository: PaymentMethodRepository,
    private val converter: PaymentMethodConverter,
) : FindPaymentMethodGateway {

    override fun execute(id: Long): Result<PaymentMethod> {
        return runCatching {
            val model = paymentMethodRepository.findById(id)
                .orElseThrow { NoSuchElementException("PaymentMethod not found: $id") }
            converter.toPaymentMethodEntity(model)
        }
    }
}
