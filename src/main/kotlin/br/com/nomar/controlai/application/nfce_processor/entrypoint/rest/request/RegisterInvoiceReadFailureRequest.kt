package br.com.nomar.controlai.application.nfce_processor.entrypoint.rest.request

import br.com.nomar.controlai.domain.purchases_invoices.entity.InvoiceReadFailureReason
import jakarta.validation.constraints.Pattern

// A reason outside InvoiceReadFailureReason fails deserialization, which answers 400
data class RegisterInvoiceReadFailureRequest(
    // Same rule as AccessKey: rejected here with 400, so the use case never sees a malformed key
    @field:Pattern(regexp = "\\d{44}")
    val accessKey: String,
    val reason: InvoiceReadFailureReason,
)
