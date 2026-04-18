package br.com.nomar.controlai.application.budget

import br.com.nomar.controlai.application.budget.converter.BudgetConverter
import br.com.nomar.controlai.application.budget.entrypoint.database.model.BudgetIncomeModel
import br.com.nomar.controlai.application.budget.entrypoint.database.model.BudgetItemModel
import br.com.nomar.controlai.application.budget.entrypoint.database.model.BudgetModel
import br.com.nomar.controlai.domain.budget.entity.Budget
import br.com.nomar.controlai.domain.budget.entity.BudgetIncome
import br.com.nomar.controlai.domain.budget.entity.BudgetItem
import br.com.nomar.controlai.domain.budget.entity.BudgetItemType
import java.math.BigDecimal
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals

class BudgetConverterTest {

    private val converter = BudgetConverter()

    @Test
    fun `toEntity should convert BudgetModel to Budget`() {
        val model = BudgetModel(id = 1, yearMonth = "2026-04")
        val itemModel = BudgetItemModel(id = 10, budget = model, categoryId = 2, type = "EXPENSE", expected = BigDecimal("1500.00"))
        val incomeModel = BudgetIncomeModel(id = 20, budget = model, label = "Ramon Salario", amount = BigDecimal("7000.00"))
        model.items.add(itemModel)
        model.incomes.add(incomeModel)

        val entity = converter.toEntity(model)

        assertEquals(1L, entity.id)
        assertEquals(YearMonth.of(2026, 4), entity.yearMonth)
        assertEquals(1, entity.items.size)
        assertEquals(2L, entity.items[0].categoryId)
        assertEquals(BudgetItemType.EXPENSE, entity.items[0].type)
        assertEquals(BigDecimal("1500.00"), entity.items[0].expected)
        assertEquals(1, entity.incomes.size)
        assertEquals("Ramon Salario", entity.incomes[0].label)
        assertEquals(BigDecimal("7000.00"), entity.incomes[0].amount)
    }

    @Test
    fun `toModel should convert Budget to BudgetModel`() {
        val entity = Budget(
            id = 1,
            yearMonth = YearMonth.of(2026, 4),
            items = listOf(
                BudgetItem(id = 10, budgetId = 1, categoryId = 2, type = BudgetItemType.INVESTMENT, expected = BigDecimal("2000.00")),
            ),
            incomes = listOf(
                BudgetIncome(id = 20, budgetId = 1, label = "Aline Salario", amount = BigDecimal("5000.00")),
            ),
        )

        val model = converter.toModel(entity)

        assertEquals(1L, model.id)
        assertEquals("2026-04", model.yearMonth)
        assertEquals(1, model.items.size)
        assertEquals("INVESTMENT", model.items[0].type)
        assertEquals(BigDecimal("2000.00"), model.items[0].expected)
        assertEquals(1, model.incomes.size)
        assertEquals("Aline Salario", model.incomes[0].label)
    }

    @Test
    fun `toEntity should handle empty items and incomes`() {
        val model = BudgetModel(id = 1, yearMonth = "2026-01")

        val entity = converter.toEntity(model)

        assertEquals(0, entity.items.size)
        assertEquals(0, entity.incomes.size)
    }

    @Test
    fun `toModel should handle empty items and incomes`() {
        val entity = Budget(yearMonth = YearMonth.of(2026, 1))

        val model = converter.toModel(entity)

        assertEquals(0, model.items.size)
        assertEquals(0, model.incomes.size)
    }
}
