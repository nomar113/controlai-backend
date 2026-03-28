package br.com.nomar.controlai.application.purchases_invoices.entrypoint.rest.response

import br.com.nomar.controlai.domain.purchases_invoices.entity.PurchaseInvoice
import java.math.BigDecimal
import java.time.OffsetDateTime

data class PurchaseInvoiceResponse(
    val id: Long?,
    val date: OffsetDateTime,
    val merchantName: String?,
    val totalItems: Int?,
    val total: BigDecimal?,
) {
    companion object {
        fun from(purchaseInvoice: PurchaseInvoice) = PurchaseInvoiceResponse(
            id = purchaseInvoice.id,
            date = purchaseInvoice.date,
            merchantName = purchaseInvoice.merchantName,
            totalItems = purchaseInvoice.totalItems.value,
            total = purchaseInvoice.total,
        )
    }
}
