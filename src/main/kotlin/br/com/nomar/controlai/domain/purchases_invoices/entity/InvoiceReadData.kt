package br.com.nomar.controlai.domain.purchases_invoices.entity

import java.math.BigDecimal
import java.time.OffsetDateTime

// Invoice data as read from the SEFAZ page by the NFC-e processor, before any consistency check.
// change (troco) only takes part in the payments rule and is never stored.
data class InvoiceReadData(
    val accessKey: String,
    val merchantName: String,
    val cnpj: String,
    val merchantAddress: String,
    val totalItems: Int,
    val subtotal: BigDecimal,
    val discount: BigDecimal,
    val taxes: BigDecimal,
    val total: BigDecimal,
    val change: BigDecimal,
    val issuedAt: OffsetDateTime?,
    val items: List<PurchaseItem>,
    val payments: List<PurchasePayment>,
)
