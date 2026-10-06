package br.com.nomar.controlai.application.purchases_invoices.entrypoint.rest.response

import br.com.nomar.controlai.application.purchases_invoices.entrypoint.database.model.PurchaseInvoiceModel
import br.com.nomar.controlai.domain.purchases_invoices.entity.InvoiceStatus
import java.time.Instant

data class PendingInvoiceResponse(
    val id: Long,
    val accessKey: String,
    val invoiceUrl: String,
    val status: InvoiceStatus,
    val date: Instant,
) {
    companion object {
        fun from(model: PurchaseInvoiceModel) = PendingInvoiceResponse(
            id = requireNotNull(model.id),
            accessKey = requireNotNull(model.accessKey),
            invoiceUrl = requireNotNull(model.invoiceUrl),
            status = model.status,
            date = model.date,
        )
    }
}
