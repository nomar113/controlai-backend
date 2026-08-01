package br.com.nomar.controlai.application.payment_methods.application

import br.com.nomar.controlai.application.payment_methods.converter.PaymentMethodConverter
import br.com.nomar.controlai.application.payment_methods.entrypoint.database.repository.PaymentMethodRepository
import br.com.nomar.controlai.domain.auth.RequestContext
import br.com.nomar.controlai.domain.payment_methods.entity.PaymentMethod
import br.com.nomar.controlai.domain.payment_methods.gateway.ListPaymentMethodsGateway
import org.springframework.stereotype.Component

@Component
class ListPaymentMethodsProvider(
    private val paymentMethodRepository: PaymentMethodRepository,
    private val converter: PaymentMethodConverter,
    private val requestContext: RequestContext,
) : ListPaymentMethodsGateway {

    override fun execute(holderId: Long?): Result<List<PaymentMethod>> {
        return runCatching {
            val models = if (holderId != null) {
                paymentMethodRepository.findAllByGroupIdAndHolderIdOrderByNameAsc(requestContext.groupId, holderId)
            } else {
                paymentMethodRepository.findAllByGroupIdOrderByNameAsc(requestContext.groupId)
            }
            models.map { converter.toPaymentMethodEntity(it) }
        }
    }
}
