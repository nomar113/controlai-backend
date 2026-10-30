package br.com.nomar.controlai.domain.purchases_invoices.entity

import java.math.BigDecimal

class PurchaseItem(
    val productName: String,
    val code: String,
    val quantity: BigDecimal,
    val unit: String,
    val unitPrice: BigDecimal,
    val totalPrice: BigDecimal,
)
