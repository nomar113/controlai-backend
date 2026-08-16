package br.com.nomar.controlai.application.budget

import br.com.nomar.controlai.application.budget.application.BudgetPeriodResolver
import br.com.nomar.controlai.application.budget.application.GetBudgetSummaryProvider
import br.com.nomar.controlai.application.budget.entrypoint.database.model.BudgetItemModel
import br.com.nomar.controlai.application.budget.entrypoint.database.model.BudgetModel
import br.com.nomar.controlai.application.budget.entrypoint.database.repository.BudgetRepository
import br.com.nomar.controlai.domain.auth.RequestContext
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyMap
import org.mockito.ArgumentMatchers.contains
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import java.math.BigDecimal
import java.sql.Date
import java.time.YearMonth
import java.util.Optional
import kotlin.test.assertEquals

// Pure Mockito unit test (no H2, no MySQL): mocks the JDBC layer directly to verify the
// aggregation glue in GetBudgetSummaryProvider.execute() correctly folds queryActualByCategory/
// queryPaymentMethodTotals results into BudgetSummary. Real SQL correctness (INNER JOIN
// installments, amount always read from the installment row) is covered separately by
// GetBudgetSummaryProviderIntegrationTest against real MySQL.
class GetBudgetSummaryProviderTest {

    private val budgetRepository: BudgetRepository = mock()
    private val jdbcTemplate: JdbcTemplate = mock()
    private val namedParameterJdbcTemplate: NamedParameterJdbcTemplate = mock()
    private val budgetPeriodResolver: BudgetPeriodResolver = mock()
    private val requestContext: RequestContext = mock<RequestContext>().also { `when`(it.groupId).thenReturn(1L) }

    private val provider = GetBudgetSummaryProvider(
        budgetRepository, jdbcTemplate, namedParameterJdbcTemplate, budgetPeriodResolver, requestContext,
    )

    private val yearMonth = YearMonth.of(2026, 2)
    private val budgetId = 10L
    private val categoryId = 20L
    private val paymentMethodId = 30L

    private fun budgetWithOneItem(): BudgetModel {
        val budget = BudgetModel(id = budgetId, groupId = 1L, yearMonth = "2026-02")
        budget.items.add(
            BudgetItemModel(id = 1L, budget = budget, categoryId = categoryId, type = "EXPENSE", expected = BigDecimal("500.00"))
        )
        return budget
    }

    private fun stubPeriodsAndCategoryLookups() {
        `when`(jdbcTemplate.queryForList(contains("budget_payment_periods bpp"), anyLong())).thenReturn(
            listOf(
                mapOf(
                    "payment_method_id" to paymentMethodId,
                    "name" to "Nubank",
                    "start_date" to Date.valueOf("2026-01-11"),
                    "end_date" to Date.valueOf("2026-02-10"),
                    "closing_day" to 10,
                )
            )
        )
        `when`(jdbcTemplate.queryForList(contains("FROM categories"), anyLong())).thenReturn(
            listOf(mapOf("id" to categoryId, "name" to "Compras", "icon" to null))
        )
    }

    @Test
    fun `execute sums the installment amount due in the month, not the purchase total, for a parceled purchase`() {
        val budget = budgetWithOneItem()
        `when`(budgetRepository.findByYearMonthAndGroupId("2026-02", 1L)).thenReturn(Optional.of(budget))
        stubPeriodsAndCategoryLookups()

        `when`(namedParameterJdbcTemplate.queryForList(contains("pn.category_id"), anyMap<String, Any>())).thenReturn(
            listOf(mapOf("category_id" to categoryId, "total" to BigDecimal("100.00")))
        )
        `when`(namedParameterJdbcTemplate.queryForList(contains("pm.id, pm.name"), anyMap<String, Any>())).thenReturn(
            listOf(mapOf("id" to paymentMethodId, "name" to "Nubank", "total" to BigDecimal("100.00")))
        )

        val summary = provider.execute(yearMonth).getOrThrow()

        assertEquals(0, BigDecimal("100.00").compareTo(summary.items.first().actual))
        assertEquals(0, BigDecimal("100.00").compareTo(summary.totalActual))
        assertEquals(0, BigDecimal("100.00").compareTo(summary.paymentMethodTotals.first().total))
    }

    @Test
    fun `execute keeps summing the total amount for a cash purchase with no installments`() {
        val budget = budgetWithOneItem()
        `when`(budgetRepository.findByYearMonthAndGroupId("2026-02", 1L)).thenReturn(Optional.of(budget))
        stubPeriodsAndCategoryLookups()

        `when`(namedParameterJdbcTemplate.queryForList(contains("pn.category_id"), anyMap<String, Any>())).thenReturn(
            listOf(mapOf("category_id" to categoryId, "total" to BigDecimal("89.90")))
        )
        `when`(namedParameterJdbcTemplate.queryForList(contains("pm.id, pm.name"), anyMap<String, Any>())).thenReturn(
            listOf(mapOf("id" to paymentMethodId, "name" to "Nubank", "total" to BigDecimal("89.90")))
        )

        val summary = provider.execute(yearMonth).getOrThrow()

        assertEquals(0, BigDecimal("89.90").compareTo(summary.items.first().actual))
        assertEquals(0, BigDecimal("89.90").compareTo(summary.totalActual))
    }

    @Test
    fun `execute returns zero actual for a category with no matching purchase this month`() {
        val budget = budgetWithOneItem()
        `when`(budgetRepository.findByYearMonthAndGroupId("2026-02", 1L)).thenReturn(Optional.of(budget))
        stubPeriodsAndCategoryLookups()

        `when`(namedParameterJdbcTemplate.queryForList(contains("pn.category_id"), anyMap<String, Any>())).thenReturn(emptyList())
        `when`(namedParameterJdbcTemplate.queryForList(contains("pm.id, pm.name"), anyMap<String, Any>())).thenReturn(emptyList())

        val summary = provider.execute(yearMonth).getOrThrow()

        assertEquals(0, BigDecimal.ZERO.compareTo(summary.items.first().actual))
        assertEquals(0, BigDecimal.ZERO.compareTo(summary.totalActual))
    }
}
