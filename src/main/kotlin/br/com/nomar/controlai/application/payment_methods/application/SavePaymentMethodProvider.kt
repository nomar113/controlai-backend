package br.com.nomar.controlai.application.payment_methods.application

import br.com.nomar.controlai.application.payment_methods.converter.PaymentMethodConverter
import br.com.nomar.controlai.application.payment_methods.entrypoint.database.repository.HolderRepository
import br.com.nomar.controlai.application.payment_methods.entrypoint.database.repository.PaymentMethodRepository
import br.com.nomar.controlai.application.payment_methods.entrypoint.database.repository.SubCardRepository
import br.com.nomar.controlai.domain.payment_methods.entity.PaymentMethod
import br.com.nomar.controlai.domain.payment_methods.gateway.SavePaymentMethodGateway
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class SavePaymentMethodProvider(
    private val paymentMethodRepository: PaymentMethodRepository,
    private val subCardRepository: SubCardRepository,
    private val holderRepository: HolderRepository,
    private val converter: PaymentMethodConverter,
) : SavePaymentMethodGateway {

    @Transactional
    override fun execute(paymentMethod: PaymentMethod): Result<PaymentMethod> {
        return runCatching {
            val holder = holderRepository.findById(paymentMethod.holderId)
                .orElseThrow { NoSuchElementException("Holder not found: ${paymentMethod.holderId}") }

            val model = converter.toPaymentMethodModel(paymentMethod, holder)
            val savedModel = paymentMethodRepository.save(model)

            val savedSubCards = paymentMethod.subCards.map { subCard ->
                val withParentId = br.com.nomar.controlai.domain.payment_methods.entity.SubCard(
                    paymentMethodId = savedModel.id!!,
                    lastFourDigits = subCard.lastFourDigits,
                    type = subCard.type,
                    nickname = subCard.nickname,
                    dependentName = subCard.dependentName,
                    walletPlatform = subCard.walletPlatform,
                )
                val subCardModel = converter.toSubCardModel(withParentId)
                subCardRepository.save(subCardModel)
            }

            converter.toPaymentMethodEntity(savedModel).let { pm ->
                PaymentMethod(
                    id = pm.id,
                    name = pm.name,
                    type = pm.type,
                    holderId = pm.holderId,
                    holder = pm.holder,
                    brand = pm.brand,
                    closingDay = pm.closingDay,
                    subCards = savedSubCards.map { converter.toSubCardEntity(it) },
                    createdAt = pm.createdAt,
                    updatedAt = pm.updatedAt,
                )
            }
        }
    }
}
