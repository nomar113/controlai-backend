package br.com.nomar.controlai.application.payment_methods.application

import br.com.nomar.controlai.application.budget.application.BudgetPeriodResolver
import br.com.nomar.controlai.domain.payment_methods.entity.PaymentMethodSummary
import br.com.nomar.controlai.domain.payment_methods.entity.SubCardTotal
import br.com.nomar.controlai.domain.payment_methods.gateway.GetPaymentMethodsSummaryGateway
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.YearMonth

@Component
class GetPaymentMethodsSummaryProvider(
    private val jdbcTemplate: JdbcTemplate,
    private val budgetPeriodResolver: BudgetPeriodResolver,
) : GetPaymentMethodsSummaryGateway {

    override fun execute(month: YearMonth): Result<List<PaymentMethodSummary>> {
        return runCatching {
            val budgetId = budgetPeriodResolver.resolveBudgetId(month)

            val rows = jdbcTemplate.queryForList(
                """
                SELECT
                    pm.id AS payment_method_id,
                    pm.name AS payment_method_name,
                    h.name AS holder_name,
                    COALESCE(sc.id, 0) AS sub_card_id,
                    COALESCE(sc.last_four_digits, '') AS last_four_digits,
                    COALESCE(SUM(pn.amount), 0) AS total
                FROM payment_methods pm
                JOIN holders h ON pm.holder_id = h.id
                LEFT JOIN sub_cards sc ON sc.payment_method_id = pm.id AND sc.deleted_at IS NULL
                LEFT JOIN budget_payment_periods bpp
                    ON bpp.payment_method_id = pm.id
                    AND bpp.budget_id = ?
                LEFT JOIN payment_notifications pn
                    ON pn.payment_method_id = pm.id
                    AND (pn.sub_card_id = sc.id OR (pn.sub_card_id IS NULL AND sc.id IS NULL))
                    AND pn.deleted_at IS NULL
                    AND pn.cancelled_at IS NULL
                    AND pn.purchased_at >= bpp.start_date
                    AND pn.purchased_at < DATE_ADD(bpp.end_date, INTERVAL 1 DAY)
                WHERE pm.deleted_at IS NULL
                GROUP BY pm.id, pm.name, h.name, sc.id, sc.last_four_digits
                ORDER BY pm.name, sc.last_four_digits
                """.trimIndent(),
                budgetId,
            )

            rows.groupBy { (it["payment_method_id"] as Number).toLong() }
                .map { (pmId, group) ->
                    val first = group.first()
                    val subCardTotals = group
                        .filter { (it["sub_card_id"] as Number).toLong() != 0L }
                        .map { row ->
                            SubCardTotal(
                                subCardId = (row["sub_card_id"] as Number).toLong(),
                                lastFourDigits = row["last_four_digits"] as String,
                                total = row["total"] as BigDecimal,
                            )
                        }
                    PaymentMethodSummary(
                        paymentMethodId = pmId,
                        name = first["payment_method_name"] as String,
                        holderName = first["holder_name"] as String,
                        totalSpent = subCardTotals.sumOf { it.total }
                            .let { if (it == BigDecimal.ZERO) (first["total"] as BigDecimal) else it },
                        subCardTotals = subCardTotals,
                    )
                }
        }
    }
}
