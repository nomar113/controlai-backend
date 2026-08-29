package br.com.nomar.controlai.domain.purchases_invoices.entity

import java.math.BigDecimal

data class ExtractedPurchaseInvoice(
    val merchantName: String,
    val cnpj: String,
    val merchantAddress: String,
    val totalItems: Int,
    val subtotal: BigDecimal,
    val discount: BigDecimal,
    val total: BigDecimal,
    val taxes: BigDecimal,
    val date: String,
    val items: List<ExtractedPurchaseItem>,
    val payments: List<ExtractedPurchasePayment>,
)
