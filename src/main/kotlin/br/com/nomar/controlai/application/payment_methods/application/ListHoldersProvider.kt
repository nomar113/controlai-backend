package br.com.nomar.controlai.application.payment_methods.application

import br.com.nomar.controlai.application.payment_methods.converter.PaymentMethodConverter
import br.com.nomar.controlai.application.payment_methods.entrypoint.database.repository.HolderRepository
import br.com.nomar.controlai.domain.auth.RequestContext
import br.com.nomar.controlai.domain.payment_methods.entity.Holder
import br.com.nomar.controlai.domain.payment_methods.gateway.ListHoldersGateway
import org.springframework.stereotype.Component

@Component
class ListHoldersProvider(
    private val holderRepository: HolderRepository,
    private val converter: PaymentMethodConverter,
    private val requestContext: RequestContext,
) : ListHoldersGateway {

    override fun execute(): Result<List<Holder>> {
        return runCatching {
            holderRepository.findAllByGroupIdOrderByNameAsc(requestContext.groupId)
                .map(converter::toHolderEntity)
        }
    }
}
