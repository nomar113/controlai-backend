package br.com.nomar.controlai.domain.payment_methods.gateway

import br.com.nomar.controlai.domain.payment_methods.entity.SubCard

fun interface SaveSubCardGateway {
    fun execute(subCard: SubCard): Result<SubCard>
}
