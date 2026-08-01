package br.com.nomar.controlai.application.budget.entrypoint.database.repository

import br.com.nomar.controlai.application.budget.entrypoint.database.model.BudgetModel
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface BudgetRepository : JpaRepository<BudgetModel, Long> {
    fun findByYearMonth(yearMonth: String): Optional<BudgetModel>
    fun findByYearMonthAndGroupId(yearMonth: String, groupId: Long): Optional<BudgetModel>
    fun findByIdAndGroupId(id: Long, groupId: Long): BudgetModel?
}
