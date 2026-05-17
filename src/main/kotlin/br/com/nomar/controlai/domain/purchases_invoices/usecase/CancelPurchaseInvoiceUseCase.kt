package br.com.nomar.controlai.domain.purchases_invoices.usecase

import br.com.nomar.controlai.domain.purchases_invoices.gateway.CancelPurchaseInvoiceGateway
import org.springframework.stereotype.Component

@Component
class CancelPurchaseInvoiceUseCase(
    private val cancelPurchaseInvoiceGateway: CancelPurchaseInvoiceGateway,
) {
    fun execute(id: Long): Result<Unit> {
        return runCatching {
            cancelPurchaseInvoiceGateway.execute(id).getOrThrow()
        }
    }
}
