package br.com.nomar.controlai.domain.payments_notifications.usecase

import br.com.nomar.controlai.application.payments_notification.entrypoint.database.model.PaymentNotification
import br.com.nomar.controlai.application.purchases_invoices.entrypoint.database.repository.PurchaseInvoiceRepository
import br.com.nomar.controlai.domain.auth.RequestContext
import br.com.nomar.controlai.domain.payments_notifications.gateway.FindInvoiceSuggestionsGateway
import org.springframework.stereotype.Component
import java.time.Instant

data class InvoiceSuggestionsResult(
    val notifications: List<PaymentNotification>,
    val invoiceDate: Instant,
)

@Component
class FindInvoiceSuggestionsUseCase(
    private val purchaseInvoiceRepository: PurchaseInvoiceRepository,
    private val findSuggestionsGateway: FindInvoiceSuggestionsGateway,
    private val requestContext: RequestContext,
) {
    fun execute(invoiceId: Long): Result<InvoiceSuggestionsResult> {
        return runCatching {
            val invoice = purchaseInvoiceRepository.findByIdAndGroupId(invoiceId, requestContext.groupId)
                ?: throw NoSuchElementException("Invoice not found: $invoiceId")

            val amount = invoice.total
                ?: throw IllegalStateException("Invoice $invoiceId has no total")

            val notifications = findSuggestionsGateway.execute(amount).getOrThrow()

            InvoiceSuggestionsResult(
                notifications = notifications,
                invoiceDate = invoice.date,
            )
        }
    }
}
