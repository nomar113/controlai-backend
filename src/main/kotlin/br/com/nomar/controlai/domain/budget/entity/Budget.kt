package br.com.nomar.controlai.domain.budget.entity

import java.time.YearMonth

class Budget(
    val id: Long? = null,
    val yearMonth: YearMonth,
    val items: List<BudgetItem> = emptyList(),
    val incomes: List<BudgetIncome> = emptyList(),
)
