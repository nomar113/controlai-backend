package br.com.nomar.controlai.domain.purchases_invoices.entity

// PENDING: only the NFC-e link and access key were captured on scan; PROCESSED: invoice data is filled in;
// READ_FAILED: the processor gave up reading the invoice from the SEFAZ (no data, like PENDING).
// Only a PROCESSED invoice is a purchase: it alone can be cancelled, associated or suggested.
enum class InvoiceStatus {
    PENDING,
    PROCESSED,
    READ_FAILED,
}
