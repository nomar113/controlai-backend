package br.com.nomar.controlai.domain.purchases_invoices.entity

// Result of registering a read failure: attempts is the counter after the failure (0 when DISCARDED)
data class FailureOutcome(val outcome: Outcome, val attempts: Int) {

    enum class Outcome {
        // Still PENDING: the processor retries it in a next run
        RETRY_SCHEDULED,
        // Reached the attempts limit
        READ_FAILED,
        // The invoice is gone, no longer PENDING or the access key does not match: nothing changed
        DISCARDED,
    }

    companion object {
        val NOTHING_CHANGED = FailureOutcome(Outcome.DISCARDED, 0)
    }
}
