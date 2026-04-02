package br.com.nomar.controlai.application.payment_methods.entrypoint.rest.response

import br.com.nomar.controlai.domain.payment_methods.entity.Holder

data class HolderResponse(
    val id: Long?,
    val name: String,
) {
    companion object {
        fun from(holder: Holder) = HolderResponse(
            id = holder.id,
            name = holder.name,
        )
    }
}
