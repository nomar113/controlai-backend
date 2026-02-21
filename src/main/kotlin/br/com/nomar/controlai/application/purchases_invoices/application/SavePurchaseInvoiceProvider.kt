package br.com.nomar.controlai.application.purchases_invoices.application

import br.com.nomar.controlai.application.purchases_invoices.converter.PurchaseInvoiceConverter
import br.com.nomar.controlai.application.purchases_invoices.entrypoint.database.repository.PurchaseInvoiceRepository
import br.com.nomar.controlai.domain.purchases_invoices.entity.PurchaseInvoice
import br.com.nomar.controlai.domain.purchases_invoices.gateway.SavePurchaseInvoiceGateway
import org.springframework.stereotype.Component

@Component
class SavePurchaseInvoiceProvider(
    private val purchaseInvoiceRepository: PurchaseInvoiceRepository,
    private val converter: PurchaseInvoiceConverter
) : SavePurchaseInvoiceGateway {

    override fun execute(purchaseInvoice: PurchaseInvoice): Result<PurchaseInvoice> {
        return runCatching {
            val existingCount = purchaseInvoiceRepository.countByAccessKey(
                purchaseInvoice.accessKey.value
            )

            if (existingCount > 0) {
                throw IllegalStateException("Já existe uma nota com essa accessKey")
            }

            val model = converter.toModel(purchaseInvoice)

            val savedModel = purchaseInvoiceRepository.save(model)

            converter.toEntity(savedModel)
        }
    }
}