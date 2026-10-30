package br.com.nomar.controlai.domain.purchases_invoices.entity

import java.time.Instant

// Oldest PENDING invoices of every group, as the NFC-e processor sees them: no group or user data.
// remaining counts the PENDING invoices left out of items by the limit.
data class PendingInvoiceQueue(val items: List<Item>, val remaining: Long) {

    data class Item(val id: Long, val invoiceUrl: String, val accessKey: String, val scannedAt: Instant)
}
