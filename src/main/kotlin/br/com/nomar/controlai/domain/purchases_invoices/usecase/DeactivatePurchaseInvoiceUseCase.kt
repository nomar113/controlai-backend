package br.com.nomar.controlai.domain.purchases_invoices.usecase

import br.com.nomar.controlai.domain.purchases_invoices.gateway.DeactivatePurchaseInvoiceGateway
import org.springframework.stereotype.Component

@Component
class DeactivatePurchaseInvoiceUseCase(
    private val deactivatePurchaseInvoiceGateway: DeactivatePurchaseInvoiceGateway,
) {
    fun execute(id: Long): Result<Unit> {
        return runCatching {
            deactivatePurchaseInvoiceGateway.execute(id).getOrThrow()
        }
    }
}
