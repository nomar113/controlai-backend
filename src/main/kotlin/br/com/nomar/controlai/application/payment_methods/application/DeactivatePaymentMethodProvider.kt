package br.com.nomar.controlai.application.payment_methods.application

import br.com.nomar.controlai.application.payment_methods.entrypoint.database.repository.PaymentMethodRepository
import br.com.nomar.controlai.domain.auth.RequestContext
import br.com.nomar.controlai.domain.payment_methods.gateway.DeactivatePaymentMethodGateway
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Component
class DeactivatePaymentMethodProvider(
    private val paymentMethodRepository: PaymentMethodRepository,
    private val requestContext: RequestContext,
) : DeactivatePaymentMethodGateway {

    @Transactional
    override fun execute(id: Long): Result<Unit> {
        return runCatching {
            val model = paymentMethodRepository.findByIdAndGroupId(id, requestContext.groupId)
                ?: throw NoSuchElementException("PaymentMethod not found: $id")

            paymentMethodRepository.save(model.copy(deletedAt = LocalDateTime.now()))
        }
    }
}
