package br.com.nomar.controlai.domain.payment_methods.gateway

import br.com.nomar.controlai.domain.payment_methods.entity.PaymentMethod

fun interface SavePaymentMethodGateway {
    fun execute(paymentMethod: PaymentMethod): Result<PaymentMethod>
}
