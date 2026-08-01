package br.com.nomar.controlai.application.payment_methods.application

import br.com.nomar.controlai.application.payment_methods.converter.PaymentMethodConverter
import br.com.nomar.controlai.application.payment_methods.entrypoint.database.repository.HolderRepository
import br.com.nomar.controlai.application.payment_methods.entrypoint.database.repository.PaymentMethodRepository
import br.com.nomar.controlai.domain.auth.RequestContext
import br.com.nomar.controlai.domain.payment_methods.entity.PaymentMethod
import br.com.nomar.controlai.domain.payment_methods.gateway.UpdatePaymentMethodGateway
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class UpdatePaymentMethodProvider(
    private val paymentMethodRepository: PaymentMethodRepository,
    private val holderRepository: HolderRepository,
    private val converter: PaymentMethodConverter,
    private val requestContext: RequestContext,
) : UpdatePaymentMethodGateway {

    @Transactional
    override fun execute(paymentMethod: PaymentMethod): Result<PaymentMethod> {
        return runCatching {
            val existing = paymentMethodRepository.findByIdAndGroupId(paymentMethod.id!!, requestContext.groupId)
                ?: throw NoSuchElementException("PaymentMethod not found: ${paymentMethod.id}")

            val holder = holderRepository.findByIdAndGroupId(paymentMethod.holderId, requestContext.groupId)
                ?: throw NoSuchElementException("Holder not found: ${paymentMethod.holderId}")

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
