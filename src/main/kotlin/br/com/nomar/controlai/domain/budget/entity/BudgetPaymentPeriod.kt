package br.com.nomar.controlai.domain.budget.entity

import java.time.LocalDate

class BudgetPaymentPeriod(
    val id: Long? = null,
    val budgetId: Long,
    val paymentMethodId: Long,
    val startDate: LocalDate,
    val endDate: LocalDate,
)
