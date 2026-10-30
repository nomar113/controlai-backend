package br.com.nomar.controlai.domain.purchases_invoices.usecase

// Counter controlai.invoices.read{outcome}, shared by the use cases called by the NFC-e processor
object InvoiceReadMetric {
    const val NAME = "controlai.invoices.read"
    const val TAG = "outcome"

    const val PROCESSED = "processed"
    const val DISCARDED = "discarded"
    const val REJECTED = "rejected"
    const val FAILED = "failed"
    const val READ_FAILED = "read_failed"
}
