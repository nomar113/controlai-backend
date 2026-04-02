package br.com.nomar.controlai.domain.payment_methods.gateway

import br.com.nomar.controlai.domain.payment_methods.entity.PaymentMethod

fun interface ListPaymentMethodsGateway {
    fun execute(holderId: Long?): Result<List<PaymentMethod>>
}
