package br.com.nomar.controlai.application.payments_notification.application

import br.com.nomar.controlai.application.budget.application.BudgetPeriodSqlSupport
import br.com.nomar.controlai.application.budget.application.ResolvedPeriod
import br.com.nomar.controlai.application.payments_notification.entrypoint.database.model.PaymentNotification
import jakarta.persistence.EntityManager
import org.springframework.stereotype.Component

/**
 * Lists/counts purchases for a month using [ResolvedPeriod]s instead of a persisted
 * `budget_payment_periods.budget_id`, so it works for months that have no Budget yet
 * (see [br.com.nomar.controlai.application.budget.application.BudgetPeriodResolver.resolvePeriods]).
 *
 * Replaces the previous `PaymentNotificationRepository.findByBudgetPeriods`/`countByBudgetPeriods`
 * static native `@Query`s, which cannot express a dynamic (per-group) number of period rows.
 */
@Component
class PaymentNotificationPeriodQueryProvider(
    private val entityManager: EntityManager,
) {

    fun findByBudgetPeriods(
        periods: List<ResolvedPeriod>,
        yearMonth: String,
        groupId: Long,
        limit: Int,
        offset: Int,
        categoryId: Long? = null,
        cardLastDigits: String? = null,
        paymentMethodId: Long? = null,
        sort: String = "recent",
    ): List<PaymentNotification> {
        val (periodsSql, periodsParams) = BudgetPeriodSqlSupport.buildNamedPeriodsDerivedTable(periods)

        val query = entityManager.createNativeQuery(
            """
            SELECT pn.* FROM payment_notifications pn
            INNER JOIN ($periodsSql) bpp
                ON pn.payment_method_id = bpp.payment_method_id
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
            """.trimIndent(),
            PaymentNotification::class.java,
        )

        periodsParams.forEach { (name, value) -> query.setParameter(name, value) }
        query.setParameter("groupId", groupId)
        query.setParameter("categoryId", categoryId)
        query.setParameter("cardLastDigits", cardLastDigits)
        query.setParameter("paymentMethodId", paymentMethodId)
        query.setParameter("yearMonth", yearMonth)
        query.setParameter("sort", sort)
        query.setParameter("limit", limit)
        query.setParameter("offset", offset)

        @Suppress("UNCHECKED_CAST")
        return query.resultList as List<PaymentNotification>
    }

    fun countByBudgetPeriods(
        periods: List<ResolvedPeriod>,
        yearMonth: String,
        groupId: Long,
        categoryId: Long? = null,
        cardLastDigits: String? = null,
        paymentMethodId: Long? = null,
    ): Long {
        val (periodsSql, periodsParams) = BudgetPeriodSqlSupport.buildNamedPeriodsDerivedTable(periods)

        val query = entityManager.createNativeQuery(
            """
            SELECT COUNT(DISTINCT pn.id) FROM payment_notifications pn
            INNER JOIN ($periodsSql) bpp
                ON pn.payment_method_id = bpp.payment_method_id
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
            """.trimIndent(),
        )

        periodsParams.forEach { (name, value) -> query.setParameter(name, value) }
        query.setParameter("groupId", groupId)
        query.setParameter("categoryId", categoryId)
        query.setParameter("cardLastDigits", cardLastDigits)
        query.setParameter("paymentMethodId", paymentMethodId)
        query.setParameter("yearMonth", yearMonth)

        return (query.singleResult as Number).toLong()
    }
}
