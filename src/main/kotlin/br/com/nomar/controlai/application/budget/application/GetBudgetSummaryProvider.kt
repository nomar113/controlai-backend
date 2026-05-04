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
) : GetBudgetSummaryGateway {

    override fun execute(yearMonth: YearMonth): Result<BudgetSummary> {
        return runCatching {
            val budgetModel = budgetRepository.findByYearMonth(yearMonth.toString())
                .orElseThrow { NoSuchElementException("Budget not found: $yearMonth") }

            val yearMonthStr = yearMonth.toString()
            val broadStart = yearMonth.minusMonths(1).atDay(1).atStartOfDay()
            val broadEnd = yearMonth.plusMonths(1).atDay(2).atStartOfDay()

            val actualByCategory = queryActualByCategory(yearMonthStr, broadStart, broadEnd)
            val paymentMethodTotals = queryPaymentMethodTotals(yearMonthStr, broadStart, broadEnd)
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

            BudgetSummary(
                budgetId = budgetModel.id!!,
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
            )
        }
    }

    private fun queryActualByCategory(yearMonth: String, broadStart: java.time.LocalDateTime, broadEnd: java.time.LocalDateTime): Map<Long, BigDecimal> {
        val rows = jdbcTemplate.queryForList(
            """
            SELECT pn.category_id, COALESCE(SUM(pn.amount), 0) AS total
            FROM payment_notifications pn
            LEFT JOIN payment_methods pm ON pn.payment_method_id = pm.id
            WHERE pn.category_id IS NOT NULL
              AND pn.deleted_at IS NULL
              AND pn.purchased_at >= ? AND pn.purchased_at < ?
              AND CASE
                WHEN pm.closing_day IS NOT NULL AND pm.type = 'CREDIT_CARD'
                  AND pm.closing_day = 1 AND DAY(pn.purchased_at) = 1
                THEN DATE_FORMAT(DATE_SUB(pn.purchased_at, INTERVAL 1 MONTH), '%Y-%m')
                WHEN pm.closing_day IS NOT NULL AND pm.type = 'CREDIT_CARD'
                  AND pm.closing_day >= 2 AND DAY(pn.purchased_at) > pm.closing_day
                THEN DATE_FORMAT(DATE_ADD(pn.purchased_at, INTERVAL 1 MONTH), '%Y-%m')
                ELSE DATE_FORMAT(pn.purchased_at, '%Y-%m')
              END = ?
            GROUP BY pn.category_id
            """.trimIndent(),
            broadStart,
            broadEnd,
            yearMonth,
        )
        return rows.associate {
            (it["category_id"] as Number).toLong() to (it["total"] as BigDecimal)
        }
    }

    private fun queryPaymentMethodTotals(yearMonth: String, broadStart: java.time.LocalDateTime, broadEnd: java.time.LocalDateTime): List<PaymentMethodTotal> {
        val rows = jdbcTemplate.queryForList(
            """
            SELECT pm.id, pm.name, COALESCE(SUM(pn.amount), 0) AS total
            FROM payment_notifications pn
            JOIN payment_methods pm ON pn.payment_method_id = pm.id
            WHERE pn.deleted_at IS NULL
              AND pn.purchased_at >= ? AND pn.purchased_at < ?
              AND CASE
                WHEN pm.closing_day IS NOT NULL AND pm.type = 'CREDIT_CARD'
                  AND pm.closing_day = 1 AND DAY(pn.purchased_at) = 1
                THEN DATE_FORMAT(DATE_SUB(pn.purchased_at, INTERVAL 1 MONTH), '%Y-%m')
                WHEN pm.closing_day IS NOT NULL AND pm.type = 'CREDIT_CARD'
                  AND pm.closing_day >= 2 AND DAY(pn.purchased_at) > pm.closing_day
                THEN DATE_FORMAT(DATE_ADD(pn.purchased_at, INTERVAL 1 MONTH), '%Y-%m')
                ELSE DATE_FORMAT(pn.purchased_at, '%Y-%m')
              END = ?
            GROUP BY pm.id, pm.name
            ORDER BY pm.name
            """.trimIndent(),
            broadStart,
            broadEnd,
            yearMonth,
        )
        return rows.map {
            PaymentMethodTotal(
                paymentMethodId = (it["id"] as Number).toLong(),
                name = it["name"] as String,
                total = it["total"] as BigDecimal,
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
