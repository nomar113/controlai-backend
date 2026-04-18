package br.com.nomar.controlai.domain.budget

import br.com.nomar.controlai.domain.budget.entity.*
import br.com.nomar.controlai.domain.budget.gateway.*
import br.com.nomar.controlai.domain.budget.usecase.*
import java.math.BigDecimal
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BudgetUseCasesTest {

    // --- SaveBudgetUseCase ---

    @Test
    fun `SaveBudgetUseCase should return saved budget on success`() {
        val gateway = SaveBudgetGateway { Result.success(Budget(id = 1, yearMonth = it.yearMonth)) }
        val useCase = SaveBudgetUseCase(gateway)

        val result = useCase.execute(Budget(yearMonth = YearMonth.of(2026, 4)))

        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrNull()?.id)
    }

    @Test
    fun `SaveBudgetUseCase should return failure when gateway fails`() {
        val gateway = SaveBudgetGateway { Result.failure(IllegalStateException("duplicate")) }
        val useCase = SaveBudgetUseCase(gateway)

        val result = useCase.execute(Budget(yearMonth = YearMonth.of(2026, 4)))

        assertTrue(result.isFailure)
        assertEquals("duplicate", result.exceptionOrNull()?.message)
    }

    // --- FindBudgetUseCase ---

    @Test
    fun `FindBudgetUseCase should return budget by yearMonth`() {
        val budget = Budget(id = 1, yearMonth = YearMonth.of(2026, 4))
        val gateway = FindBudgetGateway { Result.success(budget) }
        val useCase = FindBudgetUseCase(gateway)

        val result = useCase.execute(YearMonth.of(2026, 4))

        assertTrue(result.isSuccess)
        assertEquals(YearMonth.of(2026, 4), result.getOrNull()?.yearMonth)
    }

    @Test
    fun `FindBudgetUseCase should return failure when not found`() {
        val gateway = FindBudgetGateway { Result.failure(NoSuchElementException("not found")) }
        val useCase = FindBudgetUseCase(gateway)

        val result = useCase.execute(YearMonth.of(2026, 12))

        assertTrue(result.isFailure)
    }

    // --- DeleteBudgetUseCase ---

    @Test
    fun `DeleteBudgetUseCase should delete successfully`() {
        val gateway = DeleteBudgetGateway { Result.success(Unit) }
        val useCase = DeleteBudgetUseCase(gateway)

        val result = useCase.execute(1L)

        assertTrue(result.isSuccess)
    }

    @Test
    fun `DeleteBudgetUseCase should return failure when gateway fails`() {
        val gateway = DeleteBudgetGateway { Result.failure(NoSuchElementException("not found")) }
        val useCase = DeleteBudgetUseCase(gateway)

        val result = useCase.execute(99L)

        assertTrue(result.isFailure)
    }

    // --- DuplicateBudgetUseCase ---

    @Test
    fun `DuplicateBudgetUseCase should return duplicated budget`() {
        val target = YearMonth.of(2026, 5)
        val duplicated = Budget(
            id = 2,
            yearMonth = target,
            items = listOf(BudgetItem(id = 10, budgetId = 2, categoryId = 1, type = BudgetItemType.EXPENSE, expected = BigDecimal("1500.00"))),
            incomes = listOf(BudgetIncome(id = 20, budgetId = 2, label = "Ramon Salario", amount = BigDecimal("7000.00"))),
        )
        val gateway = DuplicateBudgetGateway { _, targetMonth ->
            assertEquals(target, targetMonth)
            Result.success(duplicated)
        }
        val useCase = DuplicateBudgetUseCase(gateway)

        val result = useCase.execute(1L, target)

        assertTrue(result.isSuccess)
        val budget = result.getOrNull()!!
        assertEquals(target, budget.yearMonth)
        assertEquals(1, budget.items.size)
        assertEquals(1, budget.incomes.size)
        assertEquals(BigDecimal("1500.00"), budget.items[0].expected)
        assertEquals("Ramon Salario", budget.incomes[0].label)
    }

    @Test
    fun `DuplicateBudgetUseCase should return failure when source not found`() {
        val gateway = DuplicateBudgetGateway { _, _ -> Result.failure(NoSuchElementException("source not found")) }
        val useCase = DuplicateBudgetUseCase(gateway)

        val result = useCase.execute(99L, YearMonth.of(2026, 5))

        assertTrue(result.isFailure)
        assertEquals("source not found", result.exceptionOrNull()?.message)
    }

    // --- SaveBudgetItemUseCase ---

    @Test
    fun `SaveBudgetItemUseCase should return saved item`() {
        val gateway = SaveBudgetItemGateway { Result.success(BudgetItem(id = 1, budgetId = it.budgetId, categoryId = it.categoryId, type = it.type, expected = it.expected)) }
        val useCase = SaveBudgetItemUseCase(gateway)

        val result = useCase.execute(BudgetItem(budgetId = 1, categoryId = 2, type = BudgetItemType.EXPENSE, expected = BigDecimal("2000.00")))

        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrNull()?.id)
    }

    @Test
    fun `SaveBudgetItemUseCase should return failure when gateway fails`() {
        val gateway = SaveBudgetItemGateway { Result.failure(IllegalStateException("duplicate")) }
        val useCase = SaveBudgetItemUseCase(gateway)

        val result = useCase.execute(BudgetItem(budgetId = 1, categoryId = 2, type = BudgetItemType.EXPENSE, expected = BigDecimal("2000.00")))

        assertTrue(result.isFailure)
    }

    // --- UpdateBudgetItemUseCase ---

    @Test
    fun `UpdateBudgetItemUseCase should return updated item`() {
        val gateway = UpdateBudgetItemGateway { Result.success(it) }
        val useCase = UpdateBudgetItemUseCase(gateway)

        val result = useCase.execute(BudgetItem(id = 1, budgetId = 1, categoryId = 2, type = BudgetItemType.EXPENSE, expected = BigDecimal("2500.00")))

        assertTrue(result.isSuccess)
        assertEquals(BigDecimal("2500.00"), result.getOrNull()?.expected)
    }

    // --- DeleteBudgetItemUseCase ---

    @Test
    fun `DeleteBudgetItemUseCase should delete successfully`() {
        val gateway = DeleteBudgetItemGateway { Result.success(Unit) }
        val useCase = DeleteBudgetItemUseCase(gateway)

        assertTrue(useCase.execute(1L).isSuccess)
    }

    // --- SaveBudgetIncomeUseCase ---

    @Test
    fun `SaveBudgetIncomeUseCase should return saved income`() {
        val gateway = SaveBudgetIncomeGateway { Result.success(BudgetIncome(id = 1, budgetId = it.budgetId, label = it.label, amount = it.amount)) }
        val useCase = SaveBudgetIncomeUseCase(gateway)

        val result = useCase.execute(BudgetIncome(budgetId = 1, label = "Ramon Salario", amount = BigDecimal("7000.00")))

        assertTrue(result.isSuccess)
        assertEquals("Ramon Salario", result.getOrNull()?.label)
    }

    // --- UpdateBudgetIncomeUseCase ---

    @Test
    fun `UpdateBudgetIncomeUseCase should return updated income`() {
        val gateway = UpdateBudgetIncomeGateway { Result.success(it) }
        val useCase = UpdateBudgetIncomeUseCase(gateway)

        val result = useCase.execute(BudgetIncome(id = 1, budgetId = 1, label = "Aline Salario", amount = BigDecimal("5000.00")))

        assertTrue(result.isSuccess)
        assertEquals(BigDecimal("5000.00"), result.getOrNull()?.amount)
    }

    // --- DeleteBudgetIncomeUseCase ---

    @Test
    fun `DeleteBudgetIncomeUseCase should delete successfully`() {
        val gateway = DeleteBudgetIncomeGateway { Result.success(Unit) }
        val useCase = DeleteBudgetIncomeUseCase(gateway)

        assertTrue(useCase.execute(1L).isSuccess)
    }

    @Test
    fun `DeleteBudgetIncomeUseCase should return failure when gateway fails`() {
        val gateway = DeleteBudgetIncomeGateway { Result.failure(NoSuchElementException("not found")) }
        val useCase = DeleteBudgetIncomeUseCase(gateway)

        val result = useCase.execute(99L)

        assertTrue(result.isFailure)
    }

    // --- GetBudgetSummaryUseCase ---

    @Test
    fun `GetBudgetSummaryUseCase should return summary`() {
        val summary = BudgetSummary(
            budgetId = 1,
            yearMonth = YearMonth.of(2026, 4),
            totalExpected = BigDecimal("8500.00"),
            totalActual = BigDecimal("6234.50"),
            percentUsed = BigDecimal("73.35"),
            totalIncome = BigDecimal("12000.00"),
            totalInvestmentExpected = BigDecimal("2000.00"),
            totalInvestmentActual = BigDecimal("1500.00"),
            items = listOf(
                BudgetItemSummary(
                    id = 10, categoryId = 2, categoryName = "Mercado", categoryIcon = "🛒", type = BudgetItemType.EXPENSE,
                    expected = BigDecimal("2000.00"), actual = BigDecimal("1834.50"), difference = BigDecimal("165.50"),
                )
            ),
            incomes = listOf(BudgetIncome(id = 1, budgetId = 1, label = "Ramon Salario", amount = BigDecimal("7000.00"))),
            paymentMethodTotals = listOf(PaymentMethodTotal(paymentMethodId = 1, name = "Nubank Ramon", total = BigDecimal("3200.00"))),
        )
        val gateway = GetBudgetSummaryGateway { Result.success(summary) }
        val useCase = GetBudgetSummaryUseCase(gateway)

        val result = useCase.execute(YearMonth.of(2026, 4))

        assertTrue(result.isSuccess)
        val s = result.getOrNull()!!
        assertEquals(BigDecimal("73.35"), s.percentUsed)
        assertEquals(1, s.items.size)
        assertEquals("Mercado", s.items[0].categoryName)
    }

    @Test
    fun `GetBudgetSummaryUseCase should return failure when no budget exists`() {
        val gateway = GetBudgetSummaryGateway { Result.failure(NoSuchElementException("no budget")) }
        val useCase = GetBudgetSummaryUseCase(gateway)

        val result = useCase.execute(YearMonth.of(2026, 12))

        assertTrue(result.isFailure)
    }

    // --- Entities ---

    @Test
    fun `Budget should be constructable with minimal fields`() {
        val budget = Budget(yearMonth = YearMonth.of(2026, 4))
        assertEquals(null, budget.id)
        assertEquals(YearMonth.of(2026, 4), budget.yearMonth)
        assertTrue(budget.items.isEmpty())
        assertTrue(budget.incomes.isEmpty())
    }

    @Test
    fun `BudgetItemType enum should have EXPENSE and INVESTMENT`() {
        assertEquals(2, BudgetItemType.entries.size)
        assertEquals(BudgetItemType.EXPENSE, BudgetItemType.valueOf("EXPENSE"))
        assertEquals(BudgetItemType.INVESTMENT, BudgetItemType.valueOf("INVESTMENT"))
    }
}
