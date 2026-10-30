package br.com.nomar.controlai.application.nfce_processor.entrypoint.rest.response

import br.com.nomar.controlai.domain.purchases_invoices.entity.FailureOutcome

data class InvoiceReadFailureResponse(
    val outcome: FailureOutcome.Outcome,
    val attempts: Int,
) {
    companion object {
        fun from(result: FailureOutcome) = InvoiceReadFailureResponse(result.outcome, result.attempts)
    }
}
