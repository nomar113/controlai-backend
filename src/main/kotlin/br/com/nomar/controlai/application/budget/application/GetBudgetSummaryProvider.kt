package br.com.nomar.controlai.application.budget.application

import br.com.nomar.controlai.application.budget.entrypoint.database.repository.BudgetRepository
import br.com.nomar.controlai.domain.budget.entity.*
import br.com.nomar.controlai.domain.budget.gateway.GetBudgetSummaryGateway
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.YearMonth

@Component
class GetBudgetSummaryProvider(
    private val budgetRepository: BudgetRepository,
    private val jdbcTemplate: JdbcTemplate,
    private val budgetPeriodResolver: BudgetPeriodResolver,
) : GetBudgetSummaryGateway {

    override fun execute(yearMonth: YearMonth): Result<BudgetSummary> {
        return runCatching {
            val budgetId = budgetPeriodResolver.resolveBudgetId(yearMonth)
            val budgetModel = budgetRepository.findByYearMonth(yearMonth.toString())
                .orElseThrow { NoSuchElementException("Budget not found: $yearMonth") }

            val actualByCategory = queryActualByCategory(budgetId)
            val paymentMethodTotals = queryPaymentMethodTotals(budgetId)
            val categoryIds = budgetModel.items.map { it.categoryId }.distinct()
            val categoryInfo = if (categoryIds.isNotEmpty()) queryCategoryInfo(categoryIds) else emptyMap()

            val items = budgetModel.items.map { item ->
                val actual = actualByCategory[item.categoryId] ?: BigDecimal.ZERO
                val info = categoryInfo[item.categoryId]
                BudgetItemSummary(
                    id = item.id!!,
                    categoryId = item.categoryId,
                    categoryName = info?.name ?: "Sem categoria",
                    categoryIcon = info?.icon,
                    type = BudgetItemType.valueOf(item.type),
                    expected = item.expected,
                    actual = actual,
                    difference = item.expected.subtract(actual),
                )
            }

            val incomes = budgetModel.incomes.map { income ->
                BudgetIncome(
                    id = income.id,
                    budgetId = budgetModel.id!!,
                    label = income.label,
                    amount = income.amount,
                )
            }

            val expenseItems = items.filter { it.type == BudgetItemType.EXPENSE }
            val investmentItems = items.filter { it.type == BudgetItemType.INVESTMENT }

            val totalExpected = expenseItems.sumOf { it.expected }
            val totalActual = expenseItems.sumOf { it.actual }
            val percentUsed = if (totalExpected > BigDecimal.ZERO) {
                totalActual.multiply(BigDecimal("100")).divide(totalExpected, 2, RoundingMode.HALF_UP)
            } else {
                BigDecimal.ZERO
            }

            val totalsMap = paymentMethodTotals.associateBy({ it.paymentMethodId }, { it.total })
            val periods = queryPeriods(budgetId).map { period ->
                BudgetPaymentPeriodSummary(
                    paymentMethodId = period.paymentMethodId,
                    paymentMethodName = period.paymentMethodName,
                    startDate = period.startDate,
                    endDate = period.endDate,
                    closingDay = period.closingDay,
                    totalAmount = totalsMap[period.paymentMethodId] ?: BigDecimal.ZERO,
                )
            }

            BudgetSummary(
                budgetId = budgetId,
                yearMonth = yearMonth,
                totalExpected = totalExpected,
                totalActual = totalActual,
                percentUsed = percentUsed,
                totalIncome = incomes.sumOf { it.amount },
                totalInvestmentExpected = investmentItems.sumOf { it.expected },
                totalInvestmentActual = investmentItems.sumOf { it.actual },
                items = items,
                incomes = incomes,
                paymentMethodTotals = paymentMethodTotals,
                periods = periods,
            )
        }
    }

    private fun queryActualByCategory(budgetId: Long): Map<Long, BigDecimal> {
        val rows = jdbcTemplate.queryForList(
            """
            SELECT pn.category_id, COALESCE(SUM(pn.amount), 0) AS total
            FROM payment_notifications pn
            INNER JOIN budget_payment_periods bpp
                ON pn.payment_method_id = bpp.payment_method_id
                AND bpp.budget_id = ?
            WHERE pn.category_id IS NOT NULL
              AND pn.deleted_at IS NULL
              AND pn.purchased_at >= bpp.start_date
              AND pn.purchased_at < DATE_ADD(bpp.end_date, INTERVAL 1 DAY)
            GROUP BY pn.category_id
            """.trimIndent(),
            budgetId,
        )
        return rows.associate {
            (it["category_id"] as Number).toLong() to (it["total"] as BigDecimal)
        }
    }

    private fun queryPaymentMethodTotals(budgetId: Long): List<PaymentMethodTotal> {
        val rows = jdbcTemplate.queryForList(
            """
            SELECT pm.id, pm.name, COALESCE(SUM(pn.amount), 0) AS total
            FROM payment_notifications pn
            INNER JOIN budget_payment_periods bpp
                ON pn.payment_method_id = bpp.payment_method_id
                AND bpp.budget_id = ?
            JOIN payment_methods pm ON pn.payment_method_id = pm.id
            WHERE pn.deleted_at IS NULL
              AND pm.deleted_at IS NULL
              AND pn.purchased_at >= bpp.start_date
              AND pn.purchased_at < DATE_ADD(bpp.end_date, INTERVAL 1 DAY)
            GROUP BY pm.id, pm.name
            ORDER BY pm.name
            """.trimIndent(),
            budgetId,
        )
        return rows.map {
            PaymentMethodTotal(
                paymentMethodId = (it["id"] as Number).toLong(),
                name = it["name"] as String,
                total = it["total"] as BigDecimal,
            )
        }
    }

    private fun queryPeriods(budgetId: Long): List<BudgetPaymentPeriodSummary> {
        val rows = jdbcTemplate.queryForList(
            """
            SELECT bpp.payment_method_id, pm.name, bpp.start_date, bpp.end_date, pm.closing_day
            FROM budget_payment_periods bpp
            JOIN payment_methods pm ON bpp.payment_method_id = pm.id
            WHERE bpp.budget_id = ?
              AND pm.deleted_at IS NULL
            ORDER BY pm.name
            """.trimIndent(),
            budgetId,
        )
        return rows.map {
            BudgetPaymentPeriodSummary(
                paymentMethodId = (it["payment_method_id"] as Number).toLong(),
                paymentMethodName = it["name"] as String,
                startDate = (it["start_date"] as java.sql.Date).toLocalDate(),
                endDate = (it["end_date"] as java.sql.Date).toLocalDate(),
                closingDay = (it["closing_day"] as Number?)?.toInt(),
            )
        }
    }

    private fun queryCategoryInfo(categoryIds: List<Long>): Map<Long, CategoryInfo> {
        val placeholders = categoryIds.joinToString(",") { "?" }
        val rows = jdbcTemplate.queryForList(
            "SELECT id, name, icon FROM categories WHERE id IN ($placeholders)",
            *categoryIds.toTypedArray(),
        )
        return rows.associate {
            (it["id"] as Number).toLong() to CategoryInfo(
                name = it["name"] as String,
                icon = it["icon"] as String?,
            )
        }
    }

    private data class CategoryInfo(val name: String, val icon: String?)
}
