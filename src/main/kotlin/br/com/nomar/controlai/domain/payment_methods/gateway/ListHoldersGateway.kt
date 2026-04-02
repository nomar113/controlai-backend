package br.com.nomar.controlai.domain.payment_methods.gateway

import br.com.nomar.controlai.domain.payment_methods.entity.Holder

fun interface ListHoldersGateway {
    fun execute(): Result<List<Holder>>
}
