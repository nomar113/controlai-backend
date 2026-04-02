package br.com.nomar.controlai.application.payment_methods.entrypoint.rest.response

import br.com.nomar.controlai.domain.payment_methods.entity.PaymentMethodSummary
import br.com.nomar.controlai.domain.payment_methods.entity.SubCardTotal
import java.math.BigDecimal

data class PaymentMethodSummaryResponse(
    val paymentMethodId: Long,
    val name: String,
    val holderName: String,
    val totalSpent: BigDecimal,
    val subCardTotals: List<SubCardTotalResponse>,
) {
    companion object {
        fun from(summary: PaymentMethodSummary) = PaymentMethodSummaryResponse(
            paymentMethodId = summary.paymentMethodId,
            name = summary.name,
            holderName = summary.holderName,
            totalSpent = summary.totalSpent,
            subCardTotals = summary.subCardTotals.map(SubCardTotalResponse::from),
        )
    }
}

data class SubCardTotalResponse(
    val subCardId: Long,
    val lastFourDigits: String,
    val total: BigDecimal,
) {
    companion object {
        fun from(subCardTotal: SubCardTotal) = SubCardTotalResponse(
            subCardId = subCardTotal.subCardId,
            lastFourDigits = subCardTotal.lastFourDigits,
            total = subCardTotal.total,
        )
    }
}
