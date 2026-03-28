package br.com.nomar.controlai.domain.purchases_invoices.usecase

import br.com.nomar.controlai.domain.purchases_invoices.entity.Purchase
import br.com.nomar.controlai.domain.purchases_invoices.gateway.ListPurchasesGateway
import org.springframework.stereotype.Component

@Component
class ListPurchasesUseCase(
    private val listPurchasesGateway: ListPurchasesGateway,
) {
    fun execute(): Result<List<Purchase>> {
        return runCatching {
            listPurchasesGateway.execute().getOrThrow()
        }
    }
}
