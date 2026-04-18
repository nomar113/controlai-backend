package br.com.nomar.controlai.application.budget.converter

import br.com.nomar.controlai.application.budget.entrypoint.database.model.BudgetIncomeModel
import br.com.nomar.controlai.application.budget.entrypoint.database.model.BudgetItemModel
import br.com.nomar.controlai.application.budget.entrypoint.database.model.BudgetModel
import br.com.nomar.controlai.domain.budget.entity.Budget
import br.com.nomar.controlai.domain.budget.entity.BudgetIncome
import br.com.nomar.controlai.domain.budget.entity.BudgetItem
import br.com.nomar.controlai.domain.budget.entity.BudgetItemType
import org.springframework.stereotype.Component
import java.time.YearMonth

@Component
class BudgetConverter {

    fun toEntity(model: BudgetModel) = Budget(
        id = model.id,
        yearMonth = YearMonth.parse(model.yearMonth),
        items = model.items.map { toItemEntity(it) },
        incomes = model.incomes.map { toIncomeEntity(it) },
    )

    fun toModel(entity: Budget): BudgetModel {
        val budgetModel = BudgetModel(
            id = entity.id,
            yearMonth = entity.yearMonth.toString(),
        )
        budgetModel.items.addAll(entity.items.map { toItemModel(it, budgetModel) })
        budgetModel.incomes.addAll(entity.incomes.map { toIncomeModel(it, budgetModel) })
        return budgetModel
    }

    fun toItemEntity(model: BudgetItemModel) = BudgetItem(
        id = model.id,
        budgetId = model.budget?.id ?: 0,
        categoryId = model.categoryId,
        type = BudgetItemType.valueOf(model.type),
        expected = model.expected,
    )

    fun toItemModel(entity: BudgetItem, budget: BudgetModel) = BudgetItemModel(
        id = entity.id,
        budget = budget,
        categoryId = entity.categoryId,
        type = entity.type.name,
        expected = entity.expected,
    )

    fun toIncomeEntity(model: BudgetIncomeModel) = BudgetIncome(
        id = model.id,
        budgetId = model.budget?.id ?: 0,
        label = model.label,
        amount = model.amount,
    )

    fun toIncomeModel(entity: BudgetIncome, budget: BudgetModel) = BudgetIncomeModel(
        id = entity.id,
        budget = budget,
        label = entity.label,
        amount = entity.amount,
    )
}
