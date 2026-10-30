package br.com.nomar.controlai.application.nfce_processor.application

import br.com.nomar.controlai.application.purchases_invoices.entrypoint.database.model.PurchaseInvoiceModel
import br.com.nomar.controlai.application.purchases_invoices.entrypoint.database.repository.PurchaseInvoiceRepository
import br.com.nomar.controlai.domain.purchases_invoices.entity.InvoiceStatus
import br.com.nomar.controlai.domain.purchases_invoices.entity.PendingInvoiceQueue
import br.com.nomar.controlai.domain.purchases_invoices.gateway.ListPendingInvoicesForProcessingGateway
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Limit
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class ListPendingInvoicesForProcessingProvider(
    private val purchaseInvoiceRepository: PurchaseInvoiceRepository,
) : ListPendingInvoicesForProcessingGateway {

    // One read-only transaction, so the page and the count see the same queue
    @Transactional(readOnly = true)
    override fun execute(limit: Int): Result<PendingInvoiceQueue> = runCatching {
        val items = purchaseInvoiceRepository
            .findByStatusOrderByCreatedAtAscIdAsc(InvoiceStatus.PENDING, Limit.of(limit))
            .mapNotNull(::toItem)
        val total = purchaseInvoiceRepository.countByStatus(InvoiceStatus.PENDING)
        PendingInvoiceQueue(items = items, remaining = (total - items.size).coerceAtLeast(0))
    }

    // A PENDING row without link or key cannot be read; skipping it keeps the rest of the queue flowing
    private fun toItem(model: PurchaseInvoiceModel): PendingInvoiceQueue.Item? {
        val invoiceUrl = model.invoiceUrl
        val accessKey = model.accessKey
        if (invoiceUrl == null || accessKey == null) {
            logger.warn("Pending invoice without link or access key skipped: invoiceId={}", model.id)
            return null
        }
        // A PENDING invoice's date is the scan time
        return PendingInvoiceQueue.Item(requireNotNull(model.id), invoiceUrl, accessKey, model.date)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ListPendingInvoicesForProcessingProvider::class.java)
    }
}
