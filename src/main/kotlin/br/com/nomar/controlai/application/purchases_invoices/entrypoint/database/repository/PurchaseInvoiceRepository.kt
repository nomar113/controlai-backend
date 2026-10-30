package br.com.nomar.controlai.application.purchases_invoices.entrypoint.database.repository

import br.com.nomar.controlai.application.purchases_invoices.entrypoint.database.model.PurchaseInvoiceModel
import br.com.nomar.controlai.domain.purchases_invoices.entity.InvoiceStatus
import org.springframework.data.domain.Limit
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.time.Instant

@Repository
interface PurchaseInvoiceRepository : JpaRepository<PurchaseInvoiceModel, Long> {

    // Soft deleted invoices are excluded by the entity's @SQLRestriction, matching the unique index
    fun existsByGroupIdAndAccessKey(groupId: Long, accessKey: String): Boolean

    fun findAllByOrderByDateDesc(): List<PurchaseInvoiceModel>

    fun findByIdAndGroupId(id: Long, groupId: Long): PurchaseInvoiceModel?

    @Query(
        value = """
            SELECT * FROM purchase_invoices pi
            WHERE pi.total = :amount
              AND pi.deleted_at IS NULL
              AND pi.cancelled_at IS NULL
              AND pi.status = 'PROCESSED'
              AND pi.group_id = :groupId
              AND pi.id NOT IN (
                  SELECT purchase_invoice_id FROM payment_notifications
                  WHERE purchase_invoice_id IS NOT NULL
              )
            ORDER BY ABS(TIMESTAMPDIFF(MINUTE, pi.date, :purchasedAt))
        """,
        nativeQuery = true
    )
    fun findByTotalAndNotAssociated(
        @Param("amount") amount: BigDecimal,
        @Param("purchasedAt") purchasedAt: Instant,
        @Param("groupId") groupId: Long,
    ): List<PurchaseInvoiceModel>

    // NFC-e processor queue (all groups); soft deleted invoices are excluded by @SQLRestriction
    fun findByStatusOrderByCreatedAtAscIdAsc(status: InvoiceStatus, limit: Limit): List<PurchaseInvoiceModel>

    fun countByStatus(status: InvoiceStatus): Long

    // Conditional on PENDING + access key, so a stale or forged failure changes nothing (0 rows).
    // MySQL evaluates single-table SET assignments left to right with the updated values, so status
    // is decided before read_attempts is incremented and both use read_attempts + 1.
    @Modifying(clearAutomatically = true)
    @Query(
        value = """
            UPDATE purchase_invoices
            SET status = CASE WHEN read_attempts + 1 >= :maxAttempts THEN 'READ_FAILED' ELSE status END,
                read_attempts = read_attempts + 1,
                last_read_failure = :reason
            WHERE id = :id
              AND access_key = :accessKey
              AND status = 'PENDING'
              AND deleted_at IS NULL
        """,
        nativeQuery = true
    )
    fun registerReadFailure(
        @Param("id") id: Long,
        @Param("accessKey") accessKey: String,
        @Param("reason") reason: String,
        @Param("maxAttempts") maxAttempts: Int,
    ): Int

    // Same condition as registerReadFailure: 0 rows when the invoice is gone, no longer PENDING or the
    // access key does not match. The row lock it takes makes a concurrent result for the same invoice
    // wait and then find it PROCESSED. invoice_url and access_key are never changed.
    @Modifying(clearAutomatically = true)
    @Query(
        value = """
            UPDATE purchase_invoices
            SET status = 'PROCESSED',
                date = :date,
                merchant_name = :merchantName,
                merchant_address = :merchantAddress,
                cnpj = :cnpj,
                total_items = :totalItems,
                subtotal = :subtotal,
                total = :total,
                taxes = :taxes,
                discount = :discount,
                processed_at = NOW(6),
                read_attempts = 0,
                last_read_failure = NULL
            WHERE id = :id
              AND access_key = :accessKey
              AND status = 'PENDING'
              AND deleted_at IS NULL
        """,
        nativeQuery = true
    )
    fun markProcessed(
        @Param("id") id: Long,
        @Param("accessKey") accessKey: String,
        @Param("date") date: Instant,
        @Param("merchantName") merchantName: String,
        @Param("merchantAddress") merchantAddress: String,
        @Param("cnpj") cnpj: String,
        @Param("totalItems") totalItems: Int,
        @Param("subtotal") subtotal: BigDecimal,
        @Param("total") total: BigDecimal,
        @Param("taxes") taxes: BigDecimal,
        @Param("discount") discount: BigDecimal,
    ): Int
}
