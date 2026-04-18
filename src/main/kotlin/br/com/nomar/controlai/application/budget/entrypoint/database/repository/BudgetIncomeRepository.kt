package br.com.nomar.controlai.application.budget.entrypoint.database.repository

import br.com.nomar.controlai.application.budget.entrypoint.database.model.BudgetIncomeModel
import org.springframework.data.jpa.repository.JpaRepository

interface BudgetIncomeRepository : JpaRepository<BudgetIncomeModel, Long>
