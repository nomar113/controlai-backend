package br.com.nomar.controlai.domain.purchases_invoices.usecase

import br.com.nomar.controlai.application.purchases_invoices.entrypoint.rest.response.AssociateInvoiceResponse
import br.com.nomar.controlai.domain.purchases_invoices.gateway.AssociateInvoiceGateway
import org.springframework.stereotype.Component

@Component
class AssociateInvoiceUseCase(
    private val associateInvoiceGateway: AssociateInvoiceGateway,
) {
    fun execute(invoiceId: Long, notificationId: Long): Result<AssociateInvoiceResponse> {
        return runCatching {
            associateInvoiceGateway.execute(invoiceId, notificationId).getOrThrow()
        }
    }
}
