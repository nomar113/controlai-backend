package br.com.nomar.controlai.application.payment_methods.converter

import br.com.nomar.controlai.application.payment_methods.entrypoint.database.model.HolderModel
import br.com.nomar.controlai.application.payment_methods.entrypoint.database.model.PaymentMethodModel
import br.com.nomar.controlai.application.payment_methods.entrypoint.database.model.SubCardModel
import br.com.nomar.controlai.domain.payment_methods.entity.*
import org.springframework.stereotype.Component

@Component
class PaymentMethodConverter {

    fun toHolderEntity(model: HolderModel) = Holder(
        id = model.id,
        name = model.name,
        createdAt = model.createdAt,
        updatedAt = model.updatedAt,
    )

    fun toHolderModel(entity: Holder) = HolderModel(
        id = entity.id,
        name = entity.name,
    )

    fun toPaymentMethodEntity(model: PaymentMethodModel): PaymentMethod = PaymentMethod(
        id = model.id,
        name = model.name,
        type = PaymentMethodType.valueOf(model.type),
        holderId = model.holderId,
        holder = model.holder?.let { toHolderEntity(it) },
        brand = model.brand,
        closingDay = model.closingDay,
        subCards = model.subCards.map { toSubCardEntity(it) },
        deletedAt = model.deletedAt,
        createdAt = model.createdAt,
        updatedAt = model.updatedAt,
    )

    fun toPaymentMethodModel(entity: PaymentMethod, holder: HolderModel? = null) = PaymentMethodModel(
        id = entity.id,
        name = entity.name,
        type = entity.type.name,
        holderId = entity.holderId,
        holder = holder,
        brand = entity.brand,
        closingDay = entity.closingDay,
        deletedAt = entity.deletedAt,
    )

    fun toSubCardEntity(model: SubCardModel) = SubCard(
        id = model.id,
        paymentMethodId = model.paymentMethodId,
        lastFourDigits = model.lastFourDigits,
        type = SubCardType.valueOf(model.type),
        nickname = model.nickname,
        dependentName = model.dependentName,
        walletPlatform = model.walletPlatform?.let { WalletPlatform.valueOf(it) },
        deletedAt = model.deletedAt,
        createdAt = model.createdAt,
        updatedAt = model.updatedAt,
    )

    fun toSubCardModel(entity: SubCard) = SubCardModel(
        id = entity.id,
        paymentMethodId = entity.paymentMethodId,
        lastFourDigits = entity.lastFourDigits,
        type = entity.type.name,
        nickname = entity.nickname,
        dependentName = entity.dependentName,
        walletPlatform = entity.walletPlatform?.name,
        deletedAt = entity.deletedAt,
    )
}
