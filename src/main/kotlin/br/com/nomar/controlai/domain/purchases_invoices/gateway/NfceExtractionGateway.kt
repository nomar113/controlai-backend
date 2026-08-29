package br.com.nomar.controlai.domain.purchases_invoices.gateway

import br.com.nomar.controlai.domain.purchases_invoices.entity.ExtractedPurchaseInvoice
import br.com.nomar.controlai.domain.purchases_invoices.entity.value_objects.InvoiceUrl

fun interface NfceExtractionGateway {
    fun extract(invoiceUrl: InvoiceUrl): Result<ExtractedPurchaseInvoice>
}
