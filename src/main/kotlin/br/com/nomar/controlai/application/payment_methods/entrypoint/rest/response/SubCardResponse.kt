package br.com.nomar.controlai.application.payment_methods.entrypoint.rest.response

import br.com.nomar.controlai.domain.payment_methods.entity.SubCard

data class SubCardResponse(
    val id: Long?,
    val lastFourDigits: String,
    val type: String,
    val nickname: String?,
    val dependentName: String?,
    val walletPlatform: String?,
) {
    companion object {
        fun from(subCard: SubCard) = SubCardResponse(
            id = subCard.id,
            lastFourDigits = subCard.lastFourDigits,
            type = subCard.type.name,
            nickname = subCard.nickname,
            dependentName = subCard.dependentName,
            walletPlatform = subCard.walletPlatform?.name,
        )
    }
}
