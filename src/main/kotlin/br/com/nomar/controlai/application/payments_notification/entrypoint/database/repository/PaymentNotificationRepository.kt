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
        LEFT JOIN payment_methods pm ON pn.payment_method_id = pm.id
        LEFT JOIN installments i ON i.parent_id = pn.id AND i.cancelled_at IS NULL
        WHERE pn.deleted_at IS NULL
          AND (
            (pn.number_of_installments <= 1
              AND pn.purchased_at >= :broadStart
              AND pn.purchased_at < :broadEnd
              AND CASE
                WHEN pm.closing_day IS NOT NULL AND pm.type = 'CREDIT_CARD'
                  AND pm.closing_day = 1 AND DAY(pn.purchased_at) = 1
                THEN DATE_FORMAT(DATE_SUB(pn.purchased_at, INTERVAL 1 MONTH), '%Y-%m')
                WHEN pm.closing_day IS NOT NULL AND pm.type = 'CREDIT_CARD'
                  AND pm.closing_day >= 2 AND DAY(pn.purchased_at) > pm.closing_day
                THEN DATE_FORMAT(DATE_ADD(pn.purchased_at, INTERVAL 1 MONTH), '%Y-%m')
                ELSE DATE_FORMAT(pn.purchased_at, '%Y-%m')
              END = :yearMonth)
            OR
            (pn.number_of_installments > 1
              AND i.id IS NOT NULL
              AND DATE_FORMAT(i.due_date, '%Y-%m') = :yearMonth)
            OR
            (pn.number_of_installments > 1
              AND NOT EXISTS (SELECT 1 FROM installments i2 WHERE i2.parent_id = pn.id AND i2.cancelled_at IS NULL)
              AND PERIOD_DIFF(
                EXTRACT(YEAR_MONTH FROM STR_TO_DATE(CONCAT(:yearMonth, '-01'), '%Y-%m-%d')),
                EXTRACT(YEAR_MONTH FROM CASE
                  WHEN pm.closing_day IS NOT NULL AND pm.type = 'CREDIT_CARD'
                    AND pm.closing_day = 1 AND DAY(pn.purchased_at) = 1
                  THEN DATE_SUB(pn.purchased_at, INTERVAL 1 MONTH)
                  WHEN pm.closing_day IS NOT NULL AND pm.type = 'CREDIT_CARD'
                    AND pm.closing_day >= 2 AND DAY(pn.purchased_at) > pm.closing_day
                  THEN DATE_ADD(pn.purchased_at, INTERVAL 1 MONTH)
                  ELSE pn.purchased_at
                END)
              ) BETWEEN 0 AND pn.number_of_installments - 1)
          )
        GROUP BY pn.id
        ORDER BY pn.purchased_at DESC
        LIMIT :limit OFFSET :offset
        """,
        nativeQuery = true
    )
    fun findByMonthRange(
        broadStart: LocalDateTime,
        broadEnd: LocalDateTime,
        yearMonth: String,
        limit: Int,
        offset: Int,
    ): List<PaymentNotification>

    @Query(
        """
        SELECT COUNT(DISTINCT pn.id) FROM payment_notifications pn
        LEFT JOIN payment_methods pm ON pn.payment_method_id = pm.id
        LEFT JOIN installments i ON i.parent_id = pn.id AND i.cancelled_at IS NULL
        WHERE pn.deleted_at IS NULL
          AND (
            (pn.number_of_installments <= 1
              AND pn.purchased_at >= :broadStart
              AND pn.purchased_at < :broadEnd
              AND CASE
                WHEN pm.closing_day IS NOT NULL AND pm.type = 'CREDIT_CARD'
                  AND pm.closing_day = 1 AND DAY(pn.purchased_at) = 1
                THEN DATE_FORMAT(DATE_SUB(pn.purchased_at, INTERVAL 1 MONTH), '%Y-%m')
                WHEN pm.closing_day IS NOT NULL AND pm.type = 'CREDIT_CARD'
                  AND pm.closing_day >= 2 AND DAY(pn.purchased_at) > pm.closing_day
                THEN DATE_FORMAT(DATE_ADD(pn.purchased_at, INTERVAL 1 MONTH), '%Y-%m')
                ELSE DATE_FORMAT(pn.purchased_at, '%Y-%m')
              END = :yearMonth)
            OR
            (pn.number_of_installments > 1
              AND i.id IS NOT NULL
              AND DATE_FORMAT(i.due_date, '%Y-%m') = :yearMonth)
            OR
            (pn.number_of_installments > 1
              AND NOT EXISTS (SELECT 1 FROM installments i2 WHERE i2.parent_id = pn.id AND i2.cancelled_at IS NULL)
              AND PERIOD_DIFF(
                EXTRACT(YEAR_MONTH FROM STR_TO_DATE(CONCAT(:yearMonth, '-01'), '%Y-%m-%d')),
                EXTRACT(YEAR_MONTH FROM CASE
                  WHEN pm.closing_day IS NOT NULL AND pm.type = 'CREDIT_CARD'
                    AND pm.closing_day = 1 AND DAY(pn.purchased_at) = 1
                  THEN DATE_SUB(pn.purchased_at, INTERVAL 1 MONTH)
                  WHEN pm.closing_day IS NOT NULL AND pm.type = 'CREDIT_CARD'
                    AND pm.closing_day >= 2 AND DAY(pn.purchased_at) > pm.closing_day
                  THEN DATE_ADD(pn.purchased_at, INTERVAL 1 MONTH)
                  ELSE pn.purchased_at
                END)
              ) BETWEEN 0 AND pn.number_of_installments - 1)
          )
        """,
        nativeQuery = true
    )
    fun countByMonthRange(
        broadStart: LocalDateTime,
        broadEnd: LocalDateTime,
        yearMonth: String,
    ): Long
}
