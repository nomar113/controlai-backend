package br.com.nomar.controlai.application.budget

import br.com.nomar.controlai.application.budget.application.BudgetPeriodCalculator
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.assertEquals

class BudgetPeriodCalculatorTest {

    private val calculator = BudgetPeriodCalculator()

    @Test
    fun `should calculate dates as first to last day of month`() {
        val (start, end) = calculator.calculateDates(YearMonth.of(2026, 5))
        assertEquals(LocalDate.of(2026, 5, 1), start)
        assertEquals(LocalDate.of(2026, 5, 31), end)
    }

    @Test
    fun `should calculate dates for february`() {
        val (start, end) = calculator.calculateDates(YearMonth.of(2026, 2))
        assertEquals(LocalDate.of(2026, 2, 1), start)
        assertEquals(LocalDate.of(2026, 2, 28), end)
    }

    @Test
    fun `should calculate dates for february leap year`() {
        val (start, end) = calculator.calculateDates(YearMonth.of(2028, 2))
        assertEquals(LocalDate.of(2028, 2, 1), start)
        assertEquals(LocalDate.of(2028, 2, 29), end)
    }
}
