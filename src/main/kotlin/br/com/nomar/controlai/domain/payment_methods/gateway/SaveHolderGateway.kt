package br.com.nomar.controlai.domain.payment_methods.gateway

import br.com.nomar.controlai.domain.payment_methods.entity.Holder

fun interface SaveHolderGateway {
    fun execute(holder: Holder): Result<Holder>
}
