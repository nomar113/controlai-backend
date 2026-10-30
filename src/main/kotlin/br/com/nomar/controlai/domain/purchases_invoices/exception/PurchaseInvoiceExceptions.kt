package br.com.nomar.controlai.domain.purchases_invoices.exception

import br.com.nomar.controlai.domain.purchases_invoices.entity.InvoiceReadFailureReason

class DuplicateInvoiceException(message: String = "Esta nota já foi registrada") : RuntimeException(message)

// Data read by the NFC-e processor broke a consistency rule; reason is what the processor reports as the failure
class InvoiceReadRejectedException(
    val reason: InvoiceReadFailureReason,
    message: String,
) : RuntimeException(message)
