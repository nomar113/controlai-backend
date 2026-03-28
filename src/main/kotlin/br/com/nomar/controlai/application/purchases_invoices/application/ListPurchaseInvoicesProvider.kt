package br.com.nomar.controlai.application.purchases_invoices.application

import br.com.nomar.controlai.application.purchases_invoices.converter.PurchaseInvoiceConverter
import br.com.nomar.controlai.application.purchases_invoices.entrypoint.database.repository.PurchaseInvoiceRepository
import br.com.nomar.controlai.domain.purchases_invoices.entity.PurchaseInvoice
import br.com.nomar.controlai.domain.purchases_invoices.gateway.ListPurchaseInvoicesGateway
import org.springframework.stereotype.Component

@Component
class ListPurchaseInvoicesProvider(
    private val purchaseInvoiceRepository: PurchaseInvoiceRepository,
    private val converter: PurchaseInvoiceConverter,
) : ListPurchaseInvoicesGateway {

    override fun execute(): Result<List<PurchaseInvoice>> {
        return runCatching {
            purchaseInvoiceRepository.findAllByOrderByDateDesc().map(converter::toEntity)
        }
    }
}
