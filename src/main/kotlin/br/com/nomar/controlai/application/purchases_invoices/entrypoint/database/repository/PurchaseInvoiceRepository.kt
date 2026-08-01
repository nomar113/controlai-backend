package br.com.nomar.controlai.application.purchases_invoices.entrypoint.database.repository

import br.com.nomar.controlai.application.purchases_invoices.entrypoint.database.model.PurchaseInvoiceModel
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.time.LocalDateTime

@Repository
interface PurchaseInvoiceRepository : JpaRepository<PurchaseInvoiceModel, Long> {

    fun countByAccessKey(accessKey: String): Long

    fun findAllByOrderByDateDesc(): List<PurchaseInvoiceModel>

    fun findByIdAndGroupId(id: Long, groupId: Long): PurchaseInvoiceModel?

    @Query(
        value = """
            SELECT * FROM purchase_invoices pi
            WHERE pi.total = :amount
              AND pi.deleted_at IS NULL
              AND pi.cancelled_at IS NULL
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
        @Param("purchasedAt") purchasedAt: LocalDateTime,
        @Param("groupId") groupId: Long,
    ): List<PurchaseInvoiceModel>
}
