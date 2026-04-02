package br.com.nomar.controlai.application.payment_methods.application

import br.com.nomar.controlai.application.payment_methods.converter.PaymentMethodConverter
import br.com.nomar.controlai.application.payment_methods.entrypoint.database.repository.SubCardRepository
import br.com.nomar.controlai.domain.payment_methods.entity.SubCard
import br.com.nomar.controlai.domain.payment_methods.gateway.SaveSubCardGateway
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class SaveSubCardProvider(
    private val subCardRepository: SubCardRepository,
    private val converter: PaymentMethodConverter,
) : SaveSubCardGateway {

    @Transactional
    override fun execute(subCard: SubCard): Result<SubCard> {
        return runCatching {
            val model = converter.toSubCardModel(subCard)
            val saved = subCardRepository.save(model)
            converter.toSubCardEntity(saved)
        }
    }
}
