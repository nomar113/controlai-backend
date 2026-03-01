package br.com.nomar.controlai.application.purchases_invoices.application

import br.com.nomar.controlai.application.purchases_invoices.converter.PurchaseInvoiceConverter
import br.com.nomar.controlai.application.purchases_invoices.entrypoint.database.repository.PurchaseItemRepository
import br.com.nomar.controlai.application.purchases_invoices.entrypoint.database.repository.PurchaseInvoiceRepository
import br.com.nomar.controlai.application.purchases_invoices.entrypoint.database.repository.PurchasePaymentRepository
import br.com.nomar.controlai.domain.purchases_invoices.entity.PurchaseInvoice
import br.com.nomar.controlai.domain.purchases_invoices.gateway.SavePurchaseInvoiceGateway
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class SavePurchaseInvoiceProvider(
    private val purchaseInvoiceRepository: PurchaseInvoiceRepository,
    private val purchaseItemRepository: PurchaseItemRepository,
    private val purchasePaymentRepository: PurchasePaymentRepository,
    private val converter: PurchaseInvoiceConverter
) : SavePurchaseInvoiceGateway {

    @Transactional
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

            val purchaseInvoiceId = savedModel.id
                ?: throw IllegalStateException("id da nota fiscal não pode ser null após persistência")

            val itemModels = converter.toItemModels(purchaseInvoice.items, purchaseInvoiceId)
            if (itemModels.isNotEmpty()) {
                purchaseItemRepository.saveAll(itemModels)
            }

            val paymentModels = converter.toPaymentModels(purchaseInvoice.payments, purchaseInvoiceId)
            if (paymentModels.isNotEmpty()) {
                purchasePaymentRepository.saveAll(paymentModels)
            }

            converter.toEntity(savedModel, purchaseInvoice.items, purchaseInvoice.payments)
        }
    }
}
