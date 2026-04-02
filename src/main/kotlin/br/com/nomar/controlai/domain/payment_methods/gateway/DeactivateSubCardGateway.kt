package br.com.nomar.controlai.domain.payment_methods.gateway

fun interface DeactivateSubCardGateway {
    fun execute(id: Long): Result<Unit>
}
