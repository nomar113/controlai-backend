package br.com.nomar.controlai.application.nfce_processor.entrypoint.rest.response

import br.com.nomar.controlai.domain.purchases_invoices.entity.PendingInvoiceQueue
import java.time.Instant

// Only what the processor needs to read the invoice: never the group or user data (PRD privacy rule)
data class PendingInvoicesForProcessingResponse(
    val items: List<Item>,
    val remaining: Long,
) {
    data class Item(val id: Long, val invoiceUrl: String, val accessKey: String, val scannedAt: Instant)

    companion object {
        fun from(queue: PendingInvoiceQueue) = PendingInvoicesForProcessingResponse(
            items = queue.items.map { Item(it.id, it.invoiceUrl, it.accessKey, it.scannedAt) },
            remaining = queue.remaining,
        )
    }
}
