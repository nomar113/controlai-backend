package br.com.nomar.controlai.application.budget.entrypoint.rest.response

import br.com.nomar.controlai.domain.budget.entity.BudgetItemSummary
import br.com.nomar.controlai.domain.budget.entity.BudgetSummary
import br.com.nomar.controlai.domain.budget.entity.PaymentMethodTotal
import java.math.BigDecimal

data class BudgetSummaryResponse(
    val budgetId: Long,
    val yearMonth: String,
    val totalExpected: BigDecimal,
    val totalActual: BigDecimal,
    val percentUsed: BigDecimal,
    val totalIncome: BigDecimal,
    val totalInvestmentExpected: BigDecimal,
    val totalInvestmentActual: BigDecimal,
    val items: List<BudgetItemSummaryResponse>,
    val incomes: List<BudgetIncomeResponse>,
    val paymentMethodTotals: List<PaymentMethodTotalResponse>,
) {
    companion object {
        fun from(summary: BudgetSummary) = BudgetSummaryResponse(
            budgetId = summary.budgetId,
            yearMonth = summary.yearMonth.toString(),
            totalExpected = summary.totalExpected,
            totalActual = summary.totalActual,
            percentUsed = summary.percentUsed,
            totalIncome = summary.totalIncome,
            totalInvestmentExpected = summary.totalInvestmentExpected,
            totalInvestmentActual = summary.totalInvestmentActual,
            items = summary.items.map(BudgetItemSummaryResponse::from),
            incomes = summary.incomes.map(BudgetIncomeResponse::from),
            paymentMethodTotals = summary.paymentMethodTotals.map(PaymentMethodTotalResponse::from),
        )
    }
}

data class BudgetItemSummaryResponse(
    val id: Long,
    val categoryId: Long,
    val categoryName: String,
    val categoryIcon: String?,
    val type: String,
    val expected: BigDecimal,
    val actual: BigDecimal,
    val difference: BigDecimal,
) {
    companion object {
        fun from(item: BudgetItemSummary) = BudgetItemSummaryResponse(
            id = item.id,
            categoryId = item.categoryId,
            categoryName = item.categoryName,
            categoryIcon = item.categoryIcon,
            type = item.type.name,
            expected = item.expected,
            actual = item.actual,
            difference = item.difference,
        )
    }
}

data class PaymentMethodTotalResponse(
    val paymentMethodId: Long,
    val name: String,
    val total: BigDecimal,
) {
    companion object {
        fun from(pmt: PaymentMethodTotal) = PaymentMethodTotalResponse(
            paymentMethodId = pmt.paymentMethodId,
            name = pmt.name,
            total = pmt.total,
        )
    }
}

data class BudgetCompactSummaryResponse(
    val yearMonth: String,
    val totalExpected: BigDecimal,
    val totalActual: BigDecimal,
    val percentUsed: BigDecimal,
    val totalIncome: BigDecimal,
    val balance: BigDecimal,
) {
    companion object {
        fun from(summary: BudgetSummary) = BudgetCompactSummaryResponse(
            yearMonth = summary.yearMonth.toString(),
            totalExpected = summary.totalExpected,
            totalActual = summary.totalActual,
            percentUsed = summary.percentUsed,
            totalIncome = summary.totalIncome,
            balance = summary.totalIncome.subtract(summary.totalActual),
        )
    }
}
