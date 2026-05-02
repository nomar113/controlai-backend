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
        WHERE pn.deleted_at IS NULL
          AND pn.purchased_at >= :startDate
          AND pn.purchased_at < :endDate
        ORDER BY pn.purchased_at DESC
        LIMIT :limit OFFSET :offset
        """,
        nativeQuery = true
    )
    fun findByMonthRange(
        startDate: LocalDateTime,
        endDate: LocalDateTime,
        limit: Int,
        offset: Int,
    ): List<PaymentNotification>

    @Query(
        """
        SELECT COUNT(*) FROM payment_notifications pn
        WHERE pn.deleted_at IS NULL
          AND pn.purchased_at >= :startDate
          AND pn.purchased_at < :endDate
        """,
        nativeQuery = true
    )
    fun countByMonthRange(
        startDate: LocalDateTime,
        endDate: LocalDateTime,
    ): Long
}
