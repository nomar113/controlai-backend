package br.com.nomar.controlai.application.payment_methods.application

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
) : GetPaymentMethodsSummaryGateway {

    override fun execute(month: YearMonth): Result<List<PaymentMethodSummary>> {
        return runCatching {
            val yearMonthStr = month.toString()
            val broadStart = month.minusMonths(1).atDay(1).atStartOfDay()
            val broadEnd = month.plusMonths(1).atDay(2).atStartOfDay()

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
                LEFT JOIN payment_notifications pn
                    ON pn.payment_method_id = pm.id
                    AND (pn.sub_card_id = sc.id OR (pn.sub_card_id IS NULL AND sc.id IS NULL))
                    AND pn.purchased_at >= ?
                    AND pn.purchased_at < ?
                    AND CASE
                      WHEN pm.closing_day IS NOT NULL AND pm.type = 'CREDIT_CARD'
                        AND pm.closing_day = 1 AND DAY(pn.purchased_at) = 1
                      THEN DATE_FORMAT(DATE_SUB(pn.purchased_at, INTERVAL 1 MONTH), '%Y-%m')
                      WHEN pm.closing_day IS NOT NULL AND pm.type = 'CREDIT_CARD'
                        AND pm.closing_day >= 1 AND DAY(pn.purchased_at) > pm.closing_day
                      THEN DATE_FORMAT(DATE_ADD(pn.purchased_at, INTERVAL 1 MONTH), '%Y-%m')
                      ELSE DATE_FORMAT(pn.purchased_at, '%Y-%m')
                    END = ?
                WHERE pm.deleted_at IS NULL
                GROUP BY pm.id, pm.name, h.name, sc.id, sc.last_four_digits
                ORDER BY pm.name, sc.last_four_digits
                """.trimIndent(),
                broadStart,
                broadEnd,
                yearMonthStr,
            )

            rows.groupBy { it["payment_method_id"] as Long }
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
