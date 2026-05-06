package br.com.nomar.controlai.application.budget.entrypoint.database.repository

import br.com.nomar.controlai.application.budget.entrypoint.database.model.BudgetPaymentPeriodModel
import org.springframework.data.jpa.repository.JpaRepository

interface BudgetPaymentPeriodRepository : JpaRepository<BudgetPaymentPeriodModel, Long> {
    fun findByBudgetId(budgetId: Long): List<BudgetPaymentPeriodModel>
}
