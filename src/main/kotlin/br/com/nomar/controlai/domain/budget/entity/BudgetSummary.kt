package br.com.nomar.controlai.domain.budget.entity

import java.math.BigDecimal
import java.time.YearMonth

class BudgetSummary(
    val budgetId: Long,
    val yearMonth: YearMonth,
    val totalExpected: BigDecimal,
    val totalActual: BigDecimal,
    val percentUsed: BigDecimal,
    val totalIncome: BigDecimal,
    val totalInvestmentExpected: BigDecimal,
    val totalInvestmentActual: BigDecimal,
    val items: List<BudgetItemSummary>,
    val incomes: List<BudgetIncome>,
    val paymentMethodTotals: List<PaymentMethodTotal>,
)

class BudgetItemSummary(
    val id: Long,
    val categoryId: Long,
    val categoryName: String,
    val categoryIcon: String?,
    val type: BudgetItemType,
    val expected: BigDecimal,
    val actual: BigDecimal,
    val difference: BigDecimal,
)

class PaymentMethodTotal(
    val paymentMethodId: Long,
    val name: String,
    val total: BigDecimal,
)
