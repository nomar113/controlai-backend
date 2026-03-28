package br.com.nomar.controlai.domain.purchases_invoices.usecase

import br.com.nomar.controlai.domain.purchases_invoices.entity.PurchaseInvoice
import br.com.nomar.controlai.domain.purchases_invoices.gateway.ListPurchaseInvoicesGateway
import org.springframework.stereotype.Component

@Component
class ListPurchaseInvoicesUseCase(
    private val listPurchaseInvoicesGateway: ListPurchaseInvoicesGateway,
) {
    fun execute(): Result<List<PurchaseInvoice>> {
        return runCatching {
            listPurchaseInvoicesGateway.execute().getOrThrow()
        }
    }
}
