package br.com.nomar.controlai.domain.budget.entity

import java.math.BigDecimal

class BudgetIncome(
    val id: Long? = null,
    val budgetId: Long,
    val label: String,
    val amount: BigDecimal,
)
