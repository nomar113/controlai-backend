package br.com.nomar.controlai.application.purchases_invoices.entrypoint.database.repository

import br.com.nomar.controlai.application.purchases_invoices.entrypoint.database.model.PurchaseInvoiceModel
import br.com.nomar.controlai.application.purchases_invoices.entrypoint.database.model.InvoiceListProjection
import br.com.nomar.controlai.application.purchases_invoices.entrypoint.database.model.PurchaseProjection
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface PurchaseRepository : JpaRepository<PurchaseInvoiceModel, Long> {

    @Query(
        """
        SELECT pn.id, pn.purchased_at AS date, pn.amount AS total, pn.merchant_name AS merchantName, NULL AS totalItems, pn.description, c.name AS categoryName, pn.category_id AS categoryId, pn.cancelled_at AS cancelledAt, 'PROCESSED' AS status
        FROM payment_notifications pn
        LEFT JOIN categories c ON pn.category_id = c.id
        WHERE pn.deleted_at IS NULL AND pn.group_id = :groupId
        UNION
        SELECT pi.id, pi.date, pi.total, pi.merchant_name AS merchantName, pi.total_items AS totalItems, pi.description, c.name AS categoryName, pi.category_id AS categoryId, pi.cancelled_at AS cancelledAt, pi.status
        FROM purchase_invoices pi
        LEFT JOIN categories c ON pi.category_id = c.id
        WHERE pi.deleted_at IS NULL AND pi.group_id = :groupId
        ORDER BY date DESC
        """,
        nativeQuery = true
    )
    fun findAllPurchases(groupId: Long): List<PurchaseProjection>

    @Query(
        """
        SELECT pi.id, pi.date, pi.total, pi.merchant_name AS merchantName, pi.total_items AS totalItems, pi.description, c.name AS categoryName, pi.category_id AS categoryId, pi.cancelled_at AS cancelledAt, pi.status
        FROM purchase_invoices pi
        LEFT JOIN categories c ON pi.category_id = c.id
        WHERE pi.deleted_at IS NULL AND pi.group_id = :groupId
        ORDER BY date DESC
        """,
        nativeQuery = true
    )
    fun findAllInvoices(groupId: Long): List<PurchaseProjection>

    @Query(
        """
        SELECT pi.id, pi.date, pi.total, pi.merchant_name AS merchantName, pi.total_items AS totalItems, pi.description, c.name AS categoryName, pi.category_id AS categoryId, pi.cancelled_at AS cancelledAt, pi.status,
               COALESCE(pi.processed_at > u.invoice_notice_seen_at
                 AND NOT EXISTS (SELECT 1 FROM invoice_views iv WHERE iv.user_id = :userId AND iv.purchase_invoice_id = pi.id), 0) AS isNew,
               CASE WHEN pi.status = 'READ_FAILED' THEN pi.last_read_failure END AS readFailureReason
        FROM purchase_invoices pi
        LEFT JOIN categories c ON pi.category_id = c.id
        LEFT JOIN users u ON u.id = :userId
        WHERE pi.deleted_at IS NULL
          AND pi.group_id = :groupId
          AND pi.date >= :startDate
          AND pi.date < :endDate
          AND (CAST(:categoryId AS SIGNED) IS NULL OR pi.category_id = :categoryId)
        ORDER BY pi.date DESC
        LIMIT :limit OFFSET :offset
        """,
        nativeQuery = true
    )
    fun findInvoicesByDateRange(
        groupId: Long,
        userId: Long,
        startDate: String,
        endDate: String,
        limit: Int,
        offset: Int,
        categoryId: Long? = null,
    ): List<InvoiceListProjection>

    @Query(
        """
        SELECT COUNT(*) FROM purchase_invoices pi
        WHERE pi.deleted_at IS NULL
          AND pi.group_id = :groupId
          AND pi.date >= :startDate
          AND pi.date < :endDate
          AND (CAST(:categoryId AS SIGNED) IS NULL OR pi.category_id = :categoryId)
        """,
        nativeQuery = true
    )
    fun countInvoicesByDateRange(
        groupId: Long,
        startDate: String,
        endDate: String,
        categoryId: Long? = null,
    ): Long
}
