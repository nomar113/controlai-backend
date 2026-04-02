package br.com.nomar.controlai.domain.payment_methods.entity

import java.math.BigDecimal

class PaymentMethodSummary(
    val paymentMethodId: Long,
    val name: String,
    val holderName: String,
    val totalSpent: BigDecimal,
    val subCardTotals: List<SubCardTotal>,
)

class SubCardTotal(
    val subCardId: Long,
    val lastFourDigits: String,
    val total: BigDecimal,
)
