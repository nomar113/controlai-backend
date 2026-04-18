package br.com.nomar.controlai.domain.budget.entity

import java.math.BigDecimal

class BudgetItem(
    val id: Long? = null,
    val budgetId: Long,
    val categoryId: Long,
    val categoryName: String? = null,
    val type: BudgetItemType,
    val expected: BigDecimal,
)
