package br.com.nomar.controlai.domain.purchases_invoices.entity

import java.math.BigDecimal

data class ExtractedPurchasePayment(
    val type: String,
    val value: BigDecimal,
)
