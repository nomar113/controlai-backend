package br.com.nomar.controlai.application.budget.application

import br.com.nomar.controlai.application.budget.entrypoint.database.model.BudgetIncomeModel
import br.com.nomar.controlai.application.budget.entrypoint.database.model.BudgetItemModel
import br.com.nomar.controlai.application.budget.entrypoint.database.model.BudgetModel

internal object BudgetCopySupport {
    fun copyItemsAndIncomes(source: BudgetModel, target: BudgetModel) {
        target.items.addAll(source.items.map { item ->
            BudgetItemModel(
                budget = target,
                categoryId = item.categoryId,
                type = item.type,
                expected = item.expected,
            )
        })

        target.incomes.addAll(source.incomes.map { income ->
            BudgetIncomeModel(
                budget = target,
                label = income.label,
                amount = income.amount,
            )
        })
    }
}
