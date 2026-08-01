package br.com.nomar.controlai.application.payment_methods.application

import br.com.nomar.controlai.application.payment_methods.converter.PaymentMethodConverter
import br.com.nomar.controlai.application.payment_methods.entrypoint.database.repository.HolderRepository
import br.com.nomar.controlai.domain.auth.RequestContext
import br.com.nomar.controlai.domain.payment_methods.entity.Holder
import br.com.nomar.controlai.domain.payment_methods.gateway.SaveHolderGateway
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class SaveHolderProvider(
    private val holderRepository: HolderRepository,
    private val converter: PaymentMethodConverter,
    private val requestContext: RequestContext,
) : SaveHolderGateway {

    @Transactional
    override fun execute(holder: Holder): Result<Holder> {
        return runCatching {
            val model = converter.toHolderModel(holder).copy(groupId = requestContext.groupId)
            val saved = holderRepository.save(model)
            converter.toHolderEntity(saved)
        }
    }
}
