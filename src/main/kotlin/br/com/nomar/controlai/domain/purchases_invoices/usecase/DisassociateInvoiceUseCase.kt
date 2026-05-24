package br.com.nomar.controlai.domain.purchases_invoices.usecase

import br.com.nomar.controlai.domain.purchases_invoices.gateway.DisassociateInvoiceGateway
import org.springframework.stereotype.Component

@Component
class DisassociateInvoiceUseCase(
    private val disassociateInvoiceGateway: DisassociateInvoiceGateway,
) {
    fun execute(invoiceId: Long): Result<Unit> {
        return runCatching {
            disassociateInvoiceGateway.execute(invoiceId).getOrThrow()
        }
    }
}
