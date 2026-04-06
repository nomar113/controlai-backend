package br.com.nomar.controlai.application.purchases_invoices.entrypoint.database.repository

import br.com.nomar.controlai.application.purchases_invoices.entrypoint.database.model.PurchaseInvoiceModel
import br.com.nomar.controlai.application.purchases_invoices.entrypoint.database.model.PurchaseProjection
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface PurchaseRepository : JpaRepository<PurchaseInvoiceModel, Long> {

    @Query(
        """
        SELECT id, purchased_at AS date, amount AS total, merchant_name AS merchantName, NULL AS totalItems, description
        FROM payment_notifications
        UNION
        SELECT id, date, total, merchant_name AS merchantName, total_items AS totalItems, description
        FROM purchase_invoices
        ORDER BY date DESC
        """,
        nativeQuery = true
    )
    fun findAllPurchases(): List<PurchaseProjection>

    @Query(
        """
        SELECT id, date, total, merchant_name AS merchantName, total_items AS totalItems, description
        FROM purchase_invoices
        ORDER BY date DESC
        """,
        nativeQuery = true
    )
    fun findAllInvoices(): List<PurchaseProjection>
}
