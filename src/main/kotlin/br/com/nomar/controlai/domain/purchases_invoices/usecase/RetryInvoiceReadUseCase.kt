package br.com.nomar.controlai.domain.purchases_invoices.usecase

import br.com.nomar.controlai.domain.purchases_invoices.gateway.RetryInvoiceReadGateway
import org.springframework.stereotype.Component

@Component
class RetryInvoiceReadUseCase(
    private val gateway: RetryInvoiceReadGateway,
) {
    fun execute(invoiceId: Long): Result<Unit> = gateway.execute(invoiceId)
}
