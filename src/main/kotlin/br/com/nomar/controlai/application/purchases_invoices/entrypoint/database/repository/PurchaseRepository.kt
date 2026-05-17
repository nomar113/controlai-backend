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
        SELECT pn.id, pn.purchased_at AS date, pn.amount AS total, pn.merchant_name AS merchantName, NULL AS totalItems, pn.description, c.name AS categoryName, pn.category_id AS categoryId, pn.cancelled_at AS cancelledAt
        FROM payment_notifications pn
        LEFT JOIN categories c ON pn.category_id = c.id
        WHERE pn.deleted_at IS NULL
        UNION
        SELECT pi.id, pi.date, pi.total, pi.merchant_name AS merchantName, pi.total_items AS totalItems, pi.description, c.name AS categoryName, pi.category_id AS categoryId, pi.cancelled_at AS cancelledAt
        FROM purchase_invoices pi
        LEFT JOIN categories c ON pi.category_id = c.id
        WHERE pi.deleted_at IS NULL
        ORDER BY date DESC
        """,
        nativeQuery = true
    )
    fun findAllPurchases(): List<PurchaseProjection>

    @Query(
        """
        SELECT pi.id, pi.date, pi.total, pi.merchant_name AS merchantName, pi.total_items AS totalItems, pi.description, c.name AS categoryName, pi.category_id AS categoryId, pi.cancelled_at AS cancelledAt
        FROM purchase_invoices pi
        LEFT JOIN categories c ON pi.category_id = c.id
        WHERE pi.deleted_at IS NULL
        ORDER BY date DESC
        """,
        nativeQuery = true
    )
    fun findAllInvoices(): List<PurchaseProjection>

    @Query(
        """
        SELECT pi.id, pi.date, pi.total, pi.merchant_name AS merchantName, pi.total_items AS totalItems, pi.description, c.name AS categoryName, pi.category_id AS categoryId, pi.cancelled_at AS cancelledAt
        FROM purchase_invoices pi
        LEFT JOIN categories c ON pi.category_id = c.id
        WHERE pi.deleted_at IS NULL
          AND pi.date >= :startDate
          AND pi.date < :endDate
          AND (CAST(:categoryId AS SIGNED) IS NULL OR pi.category_id = :categoryId)
        ORDER BY pi.date DESC
        LIMIT :limit OFFSET :offset
        """,
        nativeQuery = true
    )
    fun findInvoicesByDateRange(
        startDate: String,
        endDate: String,
        limit: Int,
        offset: Int,
        categoryId: Long? = null,
    ): List<PurchaseProjection>

    @Query(
        """
        SELECT COUNT(*) FROM purchase_invoices pi
        WHERE pi.deleted_at IS NULL
          AND pi.date >= :startDate
          AND pi.date < :endDate
          AND (CAST(:categoryId AS SIGNED) IS NULL OR pi.category_id = :categoryId)
        """,
        nativeQuery = true
    )
    fun countInvoicesByDateRange(
        startDate: String,
        endDate: String,
        categoryId: Long? = null,
    ): Long
}
