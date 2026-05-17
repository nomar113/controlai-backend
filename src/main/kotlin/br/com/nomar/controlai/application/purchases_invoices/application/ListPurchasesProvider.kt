package br.com.nomar.controlai.application.purchases_invoices.application

import br.com.nomar.controlai.application.purchases_invoices.entrypoint.database.repository.PurchaseRepository
import br.com.nomar.controlai.domain.purchases_invoices.entity.Purchase
import br.com.nomar.controlai.domain.purchases_invoices.gateway.ListPurchasesGateway
import org.springframework.stereotype.Component

@Component
class ListPurchasesProvider(
    private val purchaseRepository: PurchaseRepository,
) : ListPurchasesGateway {

    override fun execute(): Result<List<Purchase>> {
        return runCatching {
            purchaseRepository.findAllPurchases().map { projection ->
                Purchase(
                    id = projection.getId(),
                    date = projection.getDate(),
                    total = projection.getTotal(),
                    merchantName = projection.getMerchantName(),
                    totalItems = projection.getTotalItems(),
                    description = projection.getDescription(),
                    categoryName = projection.getCategoryName(),
                    cancelledAt = projection.getCancelledAt(),
                )
            }
        }
    }
}
