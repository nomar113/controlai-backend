package br.com.nomar.controlai.domain.payments_notifications.usecase

import br.com.nomar.controlai.application.payments_notification.entrypoint.database.model.PaymentNotification
import br.com.nomar.controlai.application.purchases_invoices.entrypoint.database.repository.PurchaseInvoiceRepository
import br.com.nomar.controlai.domain.payments_notifications.gateway.FindInvoiceSuggestionsGateway
import org.springframework.stereotype.Component
import java.time.OffsetDateTime

data class InvoiceSuggestionsResult(
    val notifications: List<PaymentNotification>,
    val invoiceDate: OffsetDateTime,
)

@Component
class FindInvoiceSuggestionsUseCase(
    private val purchaseInvoiceRepository: PurchaseInvoiceRepository,
    private val findSuggestionsGateway: FindInvoiceSuggestionsGateway,
) {
    fun execute(invoiceId: Long): Result<InvoiceSuggestionsResult> {
        return runCatching {
            val invoice = purchaseInvoiceRepository.findById(invoiceId)
                .orElseThrow { NoSuchElementException("Invoice not found: $invoiceId") }

            val invoiceDate = invoice.date.toLocalDateTime()
            val startDate = invoiceDate.minusHours(1)
            val endDate = invoiceDate.plusHours(1)

            val notifications = findSuggestionsGateway.execute(
                amount = invoice.total
                    ?: throw IllegalStateException("Invoice $invoiceId has no total"),
                startDate = startDate,
                endDate = endDate,
                invoiceDate = invoiceDate,
            ).getOrThrow()

            InvoiceSuggestionsResult(
                notifications = notifications,
                invoiceDate = invoice.date,
            )
        }
    }
}
