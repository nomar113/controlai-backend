package br.com.nomar.controlai.application.purchases_invoices.entrypoint.rest.request

import java.math.BigDecimal

data class PurchaseInvoiceRequest(
    val invoiceUrl: String,
    val accessKey: String,
    val cnpj: String,
    val merchantName: String,
    val merchantAddress: String,
    val date: String,
    val totalItems: Int,
    val items: List<PurchaseInvoiceItemRequest>,
    val subtotal: BigDecimal,
    val discount: BigDecimal,
    val total: BigDecimal,
    val taxes: BigDecimal,
    val payments: List<PurchaseInvoicePaymentRequest>,
)
