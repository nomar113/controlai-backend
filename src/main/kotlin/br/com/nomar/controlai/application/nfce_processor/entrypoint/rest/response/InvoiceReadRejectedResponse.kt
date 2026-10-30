package br.com.nomar.controlai.application.nfce_processor.entrypoint.rest.response

import br.com.nomar.controlai.domain.purchases_invoices.entity.InvoiceReadFailureReason
import br.com.nomar.controlai.domain.purchases_invoices.exception.InvoiceReadRejectedException

// 422 body: reason is what the processor sends back to the failure endpoint
data class InvoiceReadRejectedResponse(
    val message: String?,
    val reason: InvoiceReadFailureReason,
) {
    companion object {
        fun from(ex: InvoiceReadRejectedException) = InvoiceReadRejectedResponse(ex.message, ex.reason)
    }
}
