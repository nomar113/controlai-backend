package br.com.nomar.controlai.domain.purchases_invoices.entity

import java.math.BigDecimal

class PurchasePayment(
    val type: String,
    val value: BigDecimal,
)
