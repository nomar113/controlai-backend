package br.com.nomar.controlai.application.budget.entrypoint.database.repository

import br.com.nomar.controlai.application.budget.entrypoint.database.model.BudgetItemModel
import org.springframework.data.jpa.repository.JpaRepository

interface BudgetItemRepository : JpaRepository<BudgetItemModel, Long>
