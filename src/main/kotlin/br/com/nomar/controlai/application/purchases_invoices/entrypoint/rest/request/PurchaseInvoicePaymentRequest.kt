package br.com.nomar.controlai.application.purchases_invoices.entrypoint.rest.request

import java.math.BigDecimal

data class PurchaseInvoicePaymentRequest(
    val type: String,
    val value: BigDecimal,
)
