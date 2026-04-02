package br.com.nomar.controlai.domain.payment_methods.gateway

import br.com.nomar.controlai.domain.payment_methods.entity.PaymentMethod

fun interface UpdatePaymentMethodGateway {
    fun execute(paymentMethod: PaymentMethod): Result<PaymentMethod>
}
