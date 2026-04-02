package br.com.nomar.controlai.domain.payment_methods.gateway

fun interface DeactivatePaymentMethodGateway {
    fun execute(id: Long): Result<Unit>
}
