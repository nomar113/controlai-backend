package br.com.nomar.controlai.application.payment_methods.application

import br.com.nomar.controlai.application.payment_methods.converter.PaymentMethodConverter
import br.com.nomar.controlai.application.payment_methods.entrypoint.database.repository.SubCardRepository
import br.com.nomar.controlai.domain.payment_methods.entity.SubCard
import br.com.nomar.controlai.domain.payment_methods.gateway.UpdateSubCardGateway
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class UpdateSubCardProvider(
    private val subCardRepository: SubCardRepository,
    private val converter: PaymentMethodConverter,
) : UpdateSubCardGateway {

    @Transactional
    override fun execute(subCard: SubCard): Result<SubCard> {
        return runCatching {
            val existing = subCardRepository.findById(subCard.id!!)
                .orElseThrow { NoSuchElementException("SubCard not found: ${subCard.id}") }

            val updated = existing.copy(
                lastFourDigits = subCard.lastFourDigits,
                type = subCard.type.name,
                nickname = subCard.nickname,
                dependentName = subCard.dependentName,
                walletPlatform = subCard.walletPlatform?.name,
            )

            val savedModel = subCardRepository.save(updated)
            converter.toSubCardEntity(savedModel)
        }
    }
}
