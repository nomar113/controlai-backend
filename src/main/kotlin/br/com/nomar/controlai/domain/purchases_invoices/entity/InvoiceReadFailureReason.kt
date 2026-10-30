package br.com.nomar.controlai.domain.purchases_invoices.entity

// Why the NFC-e processor could not read an invoice from the SEFAZ; stored in last_read_failure.
// A SEFAZ block or outage is not a reason: it stops the processor run without consuming an attempt.
enum class InvoiceReadFailureReason {
    TIMEOUT,
    UNEXPECTED_LAYOUT,
    NOT_FOUND,
    INCOMPLETE_DATA,
    INCONSISTENT_DATA,
    CHALLENGE_NOT_SOLVED,
}
