package br.com.nomar.controlai.domain.purchases_invoices.entity

// Result of saving the data read by the NFC-e processor
enum class ReadOutcome {
    // The invoice was PENDING with the same access key and is now a complete PROCESSED purchase
    PROCESSED,
    // The invoice is gone, no longer PENDING or the access key does not match: nothing changed
    DISCARDED,
}
