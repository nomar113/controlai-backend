package br.com.nomar.controlai.application.payments_notification.entrypoint.database.repository

import br.com.nomar.controlai.application.payments_notification.entrypoint.database.model.PaymentNotification
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.time.LocalDateTime

@Repository
interface PaymentNotificationRepository : JpaRepository<PaymentNotification, Long> {
    fun countByCardLastDigitsAndPurchasedAtAndAmountAndMerchantNameAndNumberOfInstallmentsAndOrigin(
        cardLastDigits: String,
        purchasedAt: LocalDateTime,
        amount: BigDecimal,
        merchantName: String,
        numberOfInstallments: Int,
        origin: String,
    ): Long

    fun countByPaymentMethodIdAndPurchasedAtAndAmountAndMerchantNameAndNumberOfInstallmentsAndOrigin(
        paymentMethodId: Long?,
        purchasedAt: LocalDateTime,
        amount: BigDecimal,
        merchantName: String,
        numberOfInstallments: Int,
        origin: String,
    ): Long

    fun findByIdAndGroupId(id: Long, groupId: Long): PaymentNotification?

    fun findByGroupIdAndCancelledAtIsNull(groupId: Long): List<PaymentNotification>

    // Native query bypasses PaymentNotification's @SQLRestriction("deleted_at IS NULL") —
    // the deleted_at filter must stay explicit here.
    @Query(
        value = """
            SELECT DISTINCT group_id FROM payment_notifications
            WHERE deleted_at IS NULL
              AND cancelled_at IS NULL
        """,
        nativeQuery = true,
    )
    fun findDistinctGroupIds(): List<Long>

    @Query(
        value = """
            SELECT * FROM payment_notifications
            WHERE deleted_at IS NULL
              AND cancelled_at IS NULL
              AND group_id = :groupId
              AND amount = :amount
            ORDER BY purchased_at DESC
        """,
        nativeQuery = true
    )
    fun findSuggestionsByAmount(
        @Param("amount") amount: BigDecimal,
        @Param("groupId") groupId: Long,
    ): List<PaymentNotification>

    fun findByPurchaseInvoiceId(purchaseInvoiceId: Long): PaymentNotification?

    @Query(
        value = """
            SELECT * FROM payment_notifications
            WHERE deleted_at IS NULL
              AND cancelled_at IS NULL
              AND group_id = :groupId
              AND (purchase_invoice_id IS NULL OR purchase_invoice_id = :invoiceId)
              AND (:amount IS NULL OR amount = :amount)
              AND (:startDate IS NULL OR purchased_at >= :startDate)
              AND (:endDate IS NULL OR purchased_at <= :endDate)
            ORDER BY purchased_at DESC
            LIMIT 20
        """,
        nativeQuery = true
    )
    fun searchNotifications(
        @Param("invoiceId") invoiceId: Long,
        @Param("groupId") groupId: Long,
        @Param("amount") amount: BigDecimal?,
        @Param("startDate") startDate: LocalDateTime?,
        @Param("endDate") endDate: LocalDateTime?,
    ): List<PaymentNotification>
}
