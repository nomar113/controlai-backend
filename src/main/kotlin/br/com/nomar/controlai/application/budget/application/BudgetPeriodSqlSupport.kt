package br.com.nomar.controlai.application.budget.application

import java.sql.Date

/**
 * Builds a MySQL derived table of (payment_method_id, start_date, end_date) rows from a
 * resolved period list, so native queries can join against [ResolvedPeriod]s that may not
 * be backed by a persisted `budget_payment_periods` row (see [BudgetPeriodResolver.resolvePeriods]).
 */
object BudgetPeriodSqlSupport {

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
