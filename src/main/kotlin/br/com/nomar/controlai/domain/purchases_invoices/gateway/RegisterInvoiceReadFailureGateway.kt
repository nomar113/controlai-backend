package br.com.nomar.controlai.domain.purchases_invoices.gateway

import br.com.nomar.controlai.domain.purchases_invoices.entity.FailureOutcome
import br.com.nomar.controlai.domain.purchases_invoices.entity.InvoiceReadFailureReason
import br.com.nomar.controlai.domain.purchases_invoices.entity.value_objects.AccessKey

fun interface RegisterInvoiceReadFailureGateway {
    // Increments read_attempts; moves to READ_FAILED when attempts reach maxAttempts
    fun execute(invoiceId: Long, accessKey: AccessKey, reason: InvoiceReadFailureReason, maxAttempts: Int): Result<FailureOutcome>
}
