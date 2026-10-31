package br.com.nomar.controlai.domain.purchases_invoices.usecase

import br.com.nomar.controlai.domain.purchases_invoices.gateway.MarkInvoiceViewedGateway
import org.springframework.stereotype.Component

@Component
class MarkInvoiceViewedUseCase(
    private val gateway: MarkInvoiceViewedGateway,
) {
    fun execute(invoiceId: Long): Result<Unit> = gateway.execute(invoiceId)
}
