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

    @Query(
        """
        SELECT pn.* FROM payment_notifications pn
        INNER JOIN budget_payment_periods bpp
            ON pn.payment_method_id = bpp.payment_method_id
            AND bpp.budget_id = :budgetId
        LEFT JOIN installments i ON i.parent_id = pn.id AND i.cancelled_at IS NULL
        WHERE pn.deleted_at IS NULL
          AND pn.group_id = :groupId
          AND (CAST(:categoryId AS SIGNED) IS NULL OR pn.category_id = :categoryId)
          AND (:cardLastDigits IS NULL OR pn.card_last_digits = :cardLastDigits)
          AND (CAST(:paymentMethodId AS SIGNED) IS NULL OR pn.payment_method_id = :paymentMethodId)
          AND (
            (pn.number_of_installments <= 1
              AND pn.purchased_at >= bpp.start_date
              AND pn.purchased_at < DATE_ADD(bpp.end_date, INTERVAL 1 DAY))
            OR
            (pn.number_of_installments > 1
              AND i.id IS NOT NULL
              AND DATE_FORMAT(i.due_date, '%Y-%m') = :yearMonth)
            OR
            (pn.number_of_installments > 1
              AND pn.current_installment_number IS NOT NULL
              AND NOT EXISTS (SELECT 1 FROM installments i2 WHERE i2.parent_id = pn.id AND i2.cancelled_at IS NULL)
              AND pn.purchased_at >= bpp.start_date
              AND pn.purchased_at < DATE_ADD(bpp.end_date, INTERVAL 1 DAY))
          )
        GROUP BY pn.id
        ORDER BY CASE WHEN :sort = 'amount' THEN pn.amount END DESC, pn.purchased_at DESC
        LIMIT :limit OFFSET :offset
        """,
        nativeQuery = true
    )
    fun findByBudgetPeriods(
        budgetId: Long,
        yearMonth: String,
        groupId: Long,
        limit: Int,
        offset: Int,
        categoryId: Long? = null,
        cardLastDigits: String? = null,
        paymentMethodId: Long? = null,
        sort: String = "recent",
    ): List<PaymentNotification>

    @Query(
        """
        SELECT COUNT(DISTINCT pn.id) FROM payment_notifications pn
        INNER JOIN budget_payment_periods bpp
            ON pn.payment_method_id = bpp.payment_method_id
            AND bpp.budget_id = :budgetId
        LEFT JOIN installments i ON i.parent_id = pn.id AND i.cancelled_at IS NULL
        WHERE pn.deleted_at IS NULL
          AND pn.group_id = :groupId
          AND (CAST(:categoryId AS SIGNED) IS NULL OR pn.category_id = :categoryId)
          AND (:cardLastDigits IS NULL OR pn.card_last_digits = :cardLastDigits)
          AND (CAST(:paymentMethodId AS SIGNED) IS NULL OR pn.payment_method_id = :paymentMethodId)
          AND (
            (pn.number_of_installments <= 1
              AND pn.purchased_at >= bpp.start_date
              AND pn.purchased_at < DATE_ADD(bpp.end_date, INTERVAL 1 DAY))
            OR
            (pn.number_of_installments > 1
              AND i.id IS NOT NULL
              AND DATE_FORMAT(i.due_date, '%Y-%m') = :yearMonth)
            OR
            (pn.number_of_installments > 1
              AND pn.current_installment_number IS NOT NULL
              AND NOT EXISTS (SELECT 1 FROM installments i2 WHERE i2.parent_id = pn.id AND i2.cancelled_at IS NULL)
              AND pn.purchased_at >= bpp.start_date
              AND pn.purchased_at < DATE_ADD(bpp.end_date, INTERVAL 1 DAY))
          )
        """,
        nativeQuery = true
    )
    fun countByBudgetPeriods(
        budgetId: Long,
        yearMonth: String,
        groupId: Long,
        categoryId: Long? = null,
        cardLastDigits: String? = null,
        paymentMethodId: Long? = null,
    ): Long

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
