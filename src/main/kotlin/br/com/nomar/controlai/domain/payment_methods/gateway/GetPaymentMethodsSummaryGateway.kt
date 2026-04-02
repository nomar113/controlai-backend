package br.com.nomar.controlai.domain.payment_methods.gateway

import br.com.nomar.controlai.domain.payment_methods.entity.PaymentMethodSummary
import java.time.YearMonth

fun interface GetPaymentMethodsSummaryGateway {
    fun execute(month: YearMonth): Result<List<PaymentMethodSummary>>
}
