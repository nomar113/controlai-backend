package br.com.nomar.controlai.application.payments_notification.entrypoint.database.repository

import br.com.nomar.controlai.application.payments_notification.entrypoint.database.model.PaymentNotification
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
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

    @Query(
        """
        SELECT pn.* FROM payment_notifications pn
        INNER JOIN budget_payment_periods bpp
            ON pn.payment_method_id = bpp.payment_method_id
            AND bpp.budget_id = :budgetId
        LEFT JOIN installments i ON i.parent_id = pn.id AND i.cancelled_at IS NULL
        WHERE pn.deleted_at IS NULL
          AND (CAST(:categoryId AS SIGNED) IS NULL OR pn.category_id = :categoryId)
          AND (:cardLastDigits IS NULL OR pn.card_last_digits = :cardLastDigits)
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
        ORDER BY pn.purchased_at DESC
        LIMIT :limit OFFSET :offset
        """,
        nativeQuery = true
    )
    fun findByBudgetPeriods(
        budgetId: Long,
        yearMonth: String,
        limit: Int,
        offset: Int,
        categoryId: Long? = null,
        cardLastDigits: String? = null,
    ): List<PaymentNotification>

    @Query(
        """
        SELECT COUNT(DISTINCT pn.id) FROM payment_notifications pn
        INNER JOIN budget_payment_periods bpp
            ON pn.payment_method_id = bpp.payment_method_id
            AND bpp.budget_id = :budgetId
        LEFT JOIN installments i ON i.parent_id = pn.id AND i.cancelled_at IS NULL
        WHERE pn.deleted_at IS NULL
          AND (CAST(:categoryId AS SIGNED) IS NULL OR pn.category_id = :categoryId)
          AND (:cardLastDigits IS NULL OR pn.card_last_digits = :cardLastDigits)
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
        categoryId: Long? = null,
        cardLastDigits: String? = null,
    ): Long
}
