package br.com.nomar.controlai.domain.payment_methods.gateway

import br.com.nomar.controlai.domain.payment_methods.entity.PaymentMethod

fun interface FindPaymentMethodGateway {
    fun execute(id: Long): Result<PaymentMethod>
}
