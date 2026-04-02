package br.com.nomar.controlai.application.payment_methods.application

import br.com.nomar.controlai.application.payment_methods.converter.PaymentMethodConverter
import br.com.nomar.controlai.application.payment_methods.entrypoint.database.repository.HolderRepository
import br.com.nomar.controlai.application.payment_methods.entrypoint.database.repository.PaymentMethodRepository
import br.com.nomar.controlai.domain.payment_methods.entity.PaymentMethod
import br.com.nomar.controlai.domain.payment_methods.gateway.UpdatePaymentMethodGateway
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class UpdatePaymentMethodProvider(
    private val paymentMethodRepository: PaymentMethodRepository,
    private val holderRepository: HolderRepository,
    private val converter: PaymentMethodConverter,
) : UpdatePaymentMethodGateway {

    @Transactional
    override fun execute(paymentMethod: PaymentMethod): Result<PaymentMethod> {
        return runCatching {
            val existing = paymentMethodRepository.findById(paymentMethod.id!!)
                .orElseThrow { NoSuchElementException("PaymentMethod not found: ${paymentMethod.id}") }

            val holder = holderRepository.findById(paymentMethod.holderId)
                .orElseThrow { NoSuchElementException("Holder not found: ${paymentMethod.holderId}") }

            val updated = existing.copy(
                name = paymentMethod.name,
                type = paymentMethod.type.name,
                holder = holder,
                brand = paymentMethod.brand,
                closingDay = paymentMethod.closingDay,
            )

            val savedModel = paymentMethodRepository.save(updated)
            converter.toPaymentMethodEntity(savedModel)
        }
    }
}
