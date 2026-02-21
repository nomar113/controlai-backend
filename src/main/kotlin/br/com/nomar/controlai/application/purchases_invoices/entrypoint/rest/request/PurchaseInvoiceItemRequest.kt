package br.com.nomar.controlai.application.purchases_invoices.entrypoint.rest.request

import java.math.BigDecimal

data class PurchaseInvoiceItemRequest(
    val productName: String,
    val code: String,
    val quantity: Int,
    val unitPrice: BigDecimal,
    val unit: String,
    val totalPrice: BigDecimal,
)
