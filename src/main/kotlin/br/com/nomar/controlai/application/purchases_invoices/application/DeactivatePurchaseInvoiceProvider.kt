package br.com.nomar.controlai.application.purchases_invoices.application

import br.com.nomar.controlai.application.purchases_invoices.entrypoint.database.repository.PurchaseInvoiceRepository
import br.com.nomar.controlai.domain.purchases_invoices.gateway.DeactivatePurchaseInvoiceGateway
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Component
class DeactivatePurchaseInvoiceProvider(
    private val purchaseInvoiceRepository: PurchaseInvoiceRepository,
) : DeactivatePurchaseInvoiceGateway {

    @Transactional
    override fun execute(id: Long): Result<Unit> {
        return runCatching {
            val model = purchaseInvoiceRepository.findById(id)
                .orElseThrow { NoSuchElementException("PurchaseInvoice not found: $id") }

            purchaseInvoiceRepository.save(model.copy(deletedAt = LocalDateTime.now()))
        }
    }
}
