package br.com.nomar.controlai.application.payments_notification.entrypoint.rest.response

import br.com.nomar.controlai.application.purchases_invoices.entrypoint.database.model.PurchaseInvoiceModel
import java.math.BigDecimal
import java.time.LocalDate

data class InvoiceSuggestionResponse(
    val id: Long,
    val merchantName: String?,
    val cnpj: String?,
    val totalItems: Int?,
    val total: BigDecimal,
    val date: LocalDate,
) {
    companion object {
        fun from(invoice: PurchaseInvoiceModel) = InvoiceSuggestionResponse(
            id = invoice.id!!,
            merchantName = invoice.merchantName,
            cnpj = invoice.cnpj,
            totalItems = invoice.totalItems,
            total = invoice.total ?: BigDecimal.ZERO,
            date = invoice.date.toLocalDate(),
        )
    }
}
