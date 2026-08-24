package br.com.nomar.controlai.application.purchases_invoices.application

import br.com.nomar.controlai.application.purchases_invoices.entrypoint.database.repository.PurchaseInvoiceRepository
import br.com.nomar.controlai.domain.auth.RequestContext
import br.com.nomar.controlai.domain.purchases_invoices.gateway.CancelPurchaseInvoiceGateway
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.time.ZoneOffset

@Component
class CancelPurchaseInvoiceProvider(
    private val purchaseInvoiceRepository: PurchaseInvoiceRepository,
    private val requestContext: RequestContext,
) : CancelPurchaseInvoiceGateway {

    @Transactional
    override fun execute(id: Long): Result<Unit> {
        return runCatching {
            val model = purchaseInvoiceRepository.findByIdAndGroupId(id, requestContext.groupId)
                ?: throw NoSuchElementException("PurchaseInvoice not found: $id")

            if (model.cancelledAt != null) {
                throw IllegalStateException("PurchaseInvoice already cancelled: $id")
            }

            // cancelledAt is still LocalDateTime (not Instant — see PurchaseInvoiceDetailResponse), so it
            // must be pinned to UTC explicitly here: hibernate.jdbc.time_zone=UTC only controls how the
            // value is bound/read against the DB session (already forced to UTC), not what JVM-local
            // clock LocalDateTime.now() would otherwise capture.
            purchaseInvoiceRepository.save(model.copy(cancelledAt = LocalDateTime.now(ZoneOffset.UTC)))
        }
    }
}
