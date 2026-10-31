package br.com.nomar.controlai.application.purchases_invoices.entrypoint.database.repository

import br.com.nomar.controlai.application.auth.entrypoint.database.model.UserModel
import org.springframework.data.repository.Repository as SpringRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

// Per-user reading state of processed invoices: users.invoice_notice_seen_at and invoice_views.
// Native queries on purpose: UserModel does not map the notice column, so saving a user never touches it.

interface InvoiceNoticeRepository : SpringRepository<UserModel, Long> {

    @Query(
        value = """
            SELECT COUNT(*) FROM purchase_invoices pi
            JOIN users u ON u.id = :userId
            WHERE pi.group_id = :groupId
              AND pi.deleted_at IS NULL
              AND pi.status = 'PROCESSED'
              AND pi.processed_at > u.invoice_notice_seen_at
        """,
        nativeQuery = true
    )
    fun countProcessedSinceNotice(@Param("userId") userId: Long, @Param("groupId") groupId: Long): Long

    @Modifying(clearAutomatically = true)
    @Query(
        value = "UPDATE users SET invoice_notice_seen_at = NOW(6) WHERE id = :userId",
        nativeQuery = true
    )
    fun dismissNotice(@Param("userId") userId: Long): Int

    @Modifying(clearAutomatically = true)
    @Query(
        value = "INSERT IGNORE INTO invoice_views (user_id, purchase_invoice_id, viewed_at) VALUES (:userId, :invoiceId, NOW(6))",
        nativeQuery = true
    )
    fun markViewed(@Param("userId") userId: Long, @Param("invoiceId") invoiceId: Long): Int
}
