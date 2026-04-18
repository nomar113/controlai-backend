package br.com.nomar.controlai.application.budget.entrypoint.rest

import br.com.nomar.controlai.application.budget.entrypoint.rest.request.*
import br.com.nomar.controlai.application.budget.entrypoint.rest.response.*
import br.com.nomar.controlai.domain.budget.entity.*
import br.com.nomar.controlai.domain.budget.usecase.*
import org.springframework.http.HttpStatus
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import java.time.YearMonth

@RestController
@RequestMapping("/budgets")
class BudgetController(
    private val saveBudgetUseCase: SaveBudgetUseCase,
    private val findBudgetUseCase: FindBudgetUseCase,
    private val deleteBudgetUseCase: DeleteBudgetUseCase,
    private val duplicateBudgetUseCase: DuplicateBudgetUseCase,
    private val saveBudgetItemUseCase: SaveBudgetItemUseCase,
    private val updateBudgetItemUseCase: UpdateBudgetItemUseCase,
    private val deleteBudgetItemUseCase: DeleteBudgetItemUseCase,
    private val saveBudgetIncomeUseCase: SaveBudgetIncomeUseCase,
    private val updateBudgetIncomeUseCase: UpdateBudgetIncomeUseCase,
    private val deleteBudgetIncomeUseCase: DeleteBudgetIncomeUseCase,
    private val getBudgetSummaryUseCase: GetBudgetSummaryUseCase,
) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createBudget(@Validated @RequestBody request: CreateBudgetRequest): BudgetResponse {
        val budget = Budget(yearMonth = YearMonth.parse(request.yearMonth))
        return saveBudgetUseCase.execute(budget)
            .map(BudgetResponse::from)
            .getOrElse { throw ResponseStatusException(HttpStatus.CONFLICT, "Budget for this month already exists.") }
    }

    @GetMapping
    fun getBudget(@RequestParam month: String): BudgetSummaryResponse {
        val yearMonth = YearMonth.parse(month)
        return getBudgetSummaryUseCase.execute(yearMonth)
            .map(BudgetSummaryResponse::from)
            .getOrElse { throw ResponseStatusException(HttpStatus.NOT_FOUND, it.message) }
    }

    @GetMapping("/summary")
    fun getBudgetSummary(@RequestParam month: String): BudgetCompactSummaryResponse {
        val yearMonth = YearMonth.parse(month)
        return getBudgetSummaryUseCase.execute(yearMonth)
            .map(BudgetCompactSummaryResponse::from)
            .getOrElse { throw ResponseStatusException(HttpStatus.NOT_FOUND, it.message) }
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteBudget(@PathVariable id: Long) {
        deleteBudgetUseCase.execute(id)
            .getOrElse { throw ResponseStatusException(HttpStatus.NOT_FOUND, it.message) }
    }

    @PostMapping("/{id}/duplicate")
    @ResponseStatus(HttpStatus.CREATED)
    fun duplicateBudget(
        @PathVariable id: Long,
        @RequestParam targetMonth: String,
    ): BudgetResponse {
        val targetYearMonth = YearMonth.parse(targetMonth)
        return duplicateBudgetUseCase.execute(id, targetYearMonth)
            .map(BudgetResponse::from)
            .getOrElse {
                when (it) {
                    is NoSuchElementException -> throw ResponseStatusException(HttpStatus.NOT_FOUND, it.message)
                    else -> throw ResponseStatusException(HttpStatus.CONFLICT, "Budget for the target month already exists.")
                }
            }
    }

    // --- Budget Items ---

    @PostMapping("/{id}/items")
    @ResponseStatus(HttpStatus.CREATED)
    fun createItem(
        @PathVariable id: Long,
        @Validated @RequestBody request: CreateBudgetItemRequest,
    ): BudgetItemResponse {
        val item = BudgetItem(
            budgetId = id,
            categoryId = request.categoryId,
            type = BudgetItemType.valueOf(request.type),
            expected = request.expected,
        )
        return saveBudgetItemUseCase.execute(item)
            .map(BudgetItemResponse::from)
            .getOrElse {
                when (it) {
                    is NoSuchElementException -> throw ResponseStatusException(HttpStatus.NOT_FOUND, it.message)
                    else -> throw ResponseStatusException(HttpStatus.CONFLICT, "Duplicate item for this category.")
                }
            }
    }

    @PutMapping("/{id}/items/{itemId}")
    fun updateItem(
        @PathVariable id: Long,
        @PathVariable itemId: Long,
        @Validated @RequestBody request: UpdateBudgetItemRequest,
    ): BudgetItemResponse {
        // Only id and expected are used by UpdateBudgetItemProvider; other fields are preserved from DB
        val item = BudgetItem(
            id = itemId,
            budgetId = id,
            categoryId = 0,
            type = BudgetItemType.EXPENSE,
            expected = request.expected,
        )
        return updateBudgetItemUseCase.execute(item)
            .map(BudgetItemResponse::from)
            .getOrElse { throw ResponseStatusException(HttpStatus.NOT_FOUND, it.message) }
    }

    @DeleteMapping("/{id}/items/{itemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteItem(@PathVariable id: Long, @PathVariable itemId: Long) {
        deleteBudgetItemUseCase.execute(itemId)
            .getOrElse { throw ResponseStatusException(HttpStatus.NOT_FOUND, it.message) }
    }

    // --- Budget Incomes ---

    @PostMapping("/{id}/incomes")
    @ResponseStatus(HttpStatus.CREATED)
    fun createIncome(
        @PathVariable id: Long,
        @Validated @RequestBody request: CreateBudgetIncomeRequest,
    ): BudgetIncomeResponse {
        val income = BudgetIncome(
            budgetId = id,
            label = request.label,
            amount = request.amount,
        )
        return saveBudgetIncomeUseCase.execute(income)
            .map(BudgetIncomeResponse::from)
            .getOrElse { throw ResponseStatusException(HttpStatus.NOT_FOUND, it.message) }
    }

    @PutMapping("/{id}/incomes/{incomeId}")
    fun updateIncome(
        @PathVariable id: Long,
        @PathVariable incomeId: Long,
        @Validated @RequestBody request: UpdateBudgetIncomeRequest,
    ): BudgetIncomeResponse {
        val income = BudgetIncome(
            id = incomeId,
            budgetId = id,
            label = request.label,
            amount = request.amount,
        )
        return updateBudgetIncomeUseCase.execute(income)
            .map(BudgetIncomeResponse::from)
            .getOrElse { throw ResponseStatusException(HttpStatus.NOT_FOUND, it.message) }
    }

    @DeleteMapping("/{id}/incomes/{incomeId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteIncome(@PathVariable id: Long, @PathVariable incomeId: Long) {
        deleteBudgetIncomeUseCase.execute(incomeId)
            .getOrElse { throw ResponseStatusException(HttpStatus.NOT_FOUND, it.message) }
    }
}
