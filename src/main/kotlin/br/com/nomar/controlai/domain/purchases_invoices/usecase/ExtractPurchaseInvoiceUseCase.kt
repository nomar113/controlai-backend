package br.com.nomar.controlai.domain.purchases_invoices.usecase

import br.com.nomar.controlai.application.purchases_invoices.entrypoint.database.repository.PurchaseInvoiceRepository
import br.com.nomar.controlai.domain.purchases_invoices.entity.ExtractedPurchaseInvoice
import br.com.nomar.controlai.domain.purchases_invoices.entity.value_objects.AccessKey
import br.com.nomar.controlai.domain.purchases_invoices.entity.value_objects.InvoiceUrl
import br.com.nomar.controlai.domain.purchases_invoices.gateway.NfceExtractionGateway
import org.springframework.stereotype.Component

@Component
class ExtractPurchaseInvoiceUseCase(
    private val extractionGateway: NfceExtractionGateway,
    private val purchaseInvoiceRepository: PurchaseInvoiceRepository,
) {
    fun execute(invoiceUrl: InvoiceUrl): Result<ExtractedPurchaseInvoice> = runCatching {
        val accessKey = AccessKey.fromInvoiceUrl(invoiceUrl)
        check(purchaseInvoiceRepository.countByAccessKey(accessKey.value) == 0L) { "Nota já registrada" }
        extractionGateway.extract(invoiceUrl).getOrThrow()
    }
}
