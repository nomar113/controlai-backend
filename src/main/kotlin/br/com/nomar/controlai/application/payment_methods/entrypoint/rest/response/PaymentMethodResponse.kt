package br.com.nomar.controlai.application.payment_methods.entrypoint.rest.response

import br.com.nomar.controlai.domain.payment_methods.entity.PaymentMethod

data class PaymentMethodResponse(
    val id: Long?,
    val name: String,
    val type: String,
    val holder: HolderResponse?,
    val brand: String?,
    val closingDay: Int?,
    val subCards: List<SubCardResponse>,
) {
    companion object {
        fun from(pm: PaymentMethod) = PaymentMethodResponse(
            id = pm.id,
            name = pm.name,
            type = pm.type.name,
            holder = pm.holder?.let { HolderResponse.from(it) },
            brand = pm.brand,
            closingDay = pm.closingDay,
            subCards = pm.subCards.map(SubCardResponse::from),
        )
    }
}
