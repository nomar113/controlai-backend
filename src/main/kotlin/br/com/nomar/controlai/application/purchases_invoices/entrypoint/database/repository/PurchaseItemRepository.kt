package br.com.nomar.controlai.application.purchases_invoices.entrypoint.database.repository

import br.com.nomar.controlai.application.purchases_invoices.entrypoint.database.model.PurchaseItemModel
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface PurchaseItemRepository : JpaRepository<PurchaseItemModel, Long> {
    fun findByPurchaseInvoiceId(purchaseInvoiceId: Long): List<PurchaseItemModel>
}
