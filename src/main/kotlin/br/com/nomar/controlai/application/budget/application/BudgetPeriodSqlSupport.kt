package br.com.nomar.controlai.application.budget.application

import java.sql.Date

/**
 * Builds a MySQL derived table of (payment_method_id, start_date, end_date) rows from a
 * resolved period list, so native queries can join against [ResolvedPeriod]s that may not
 * be backed by a persisted `budget_payment_periods` row (see [BudgetPeriodResolver.resolvePeriods]).
 */
object BudgetPeriodSqlSupport {

    /** Joins a `payment_notifications pn` row to its installments, so [PERIOD_MATCH_PREDICATE] can tell cash and installment-billed rows apart. */
    const val INSTALLMENTS_JOIN = "LEFT JOIN installments i ON i.parent_id = pn.id AND i.cancelled_at IS NULL"

    /**
     * Matches a `payment_notifications pn` row (joined via [INSTALLMENTS_JOIN]) against a period
     * row aliased `bpp` (columns `payment_method_id`/`start_date`/`end_date`, either a persisted
     * `budget_payment_periods` row or a [buildNamedPeriodsDerivedTable] row) for a `:yearMonth`
     * bind parameter. Cash purchases match by `purchased_at` falling inside the cycle; installment
     * purchases match by the specific installment's `due_date` falling in `:yearMonth`;
     * pre-reconciliation parceladas without `installments` rows fall back to `purchased_at`; this
     * fallback becomes dead once a boot-time reconciliation backfills installments for all
     * existing parceladas.
     */
    val PERIOD_MATCH_PREDICATE = """
        (
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
    """.trimIndent()

    /** Amount to aggregate for a row matched by [PERIOD_MATCH_PREDICATE]: the installment's own amount when billed via a real installment row, otherwise the notification's total. */
    const val AMOUNT_EXPRESSION = "CASE WHEN pn.number_of_installments > 1 AND i.id IS NOT NULL THEN i.amount ELSE pn.amount END"

    fun buildPeriodsDerivedTable(periods: List<ResolvedPeriod>): Pair<String, List<Any>> {
        if (periods.isEmpty()) {
            return "SELECT NULL AS payment_method_id, NULL AS start_date, NULL AS end_date FROM DUAL WHERE 1 = 0" to emptyList()
        }

        val sql = periods.joinToString(separator = " UNION ALL ") { "SELECT ? AS payment_method_id, ? AS start_date, ? AS end_date" }
        val params = periods.flatMap { listOf(it.paymentMethodId, Date.valueOf(it.startDate), Date.valueOf(it.endDate)) }
        return sql to params
    }

    /** Same derived table, but with named (`:pmId0`, ...) placeholders for JPA native queries mixing named params. */
    fun buildNamedPeriodsDerivedTable(periods: List<ResolvedPeriod>): Pair<String, Map<String, Any>> {
        if (periods.isEmpty()) {
            return "SELECT NULL AS payment_method_id, NULL AS start_date, NULL AS end_date FROM DUAL WHERE 1 = 0" to emptyMap()
        }

        val sql = periods.indices.joinToString(separator = " UNION ALL ") { i ->
            "SELECT :pmId$i AS payment_method_id, :startDate$i AS start_date, :endDate$i AS end_date"
        }
        val params = periods.withIndex().flatMap { (i, period) ->
            listOf(
                "pmId$i" to period.paymentMethodId,
                "startDate$i" to Date.valueOf(period.startDate),
                "endDate$i" to Date.valueOf(period.endDate),
            )
        }.toMap()
        return sql to params
    }
}
