package br.com.nomar.controlai.application.budget

import br.com.nomar.controlai.application.budget.application.BudgetPeriodCalculator
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.assertEquals

class BudgetPeriodCalculatorTest {

    private val calculator = BudgetPeriodCalculator()

    @Test
    fun `should calculate credit card dates for closingDay 10`() {
        val (start, end) = calculator.calculateDates(10, "CREDIT_CARD", YearMonth.of(2026, 5))
        assertEquals(LocalDate.of(2026, 4, 11), start)
        assertEquals(LocalDate.of(2026, 5, 10), end)
    }

    @Test
    fun `should calculate credit card dates for closingDay 3`() {
        val (start, end) = calculator.calculateDates(3, "CREDIT_CARD", YearMonth.of(2026, 5))
        assertEquals(LocalDate.of(2026, 4, 4), start)
        assertEquals(LocalDate.of(2026, 5, 3), end)
    }

    @Test
    fun `should calculate credit card dates for closingDay 15`() {
        val (start, end) = calculator.calculateDates(15, "CREDIT_CARD", YearMonth.of(2026, 6))
        assertEquals(LocalDate.of(2026, 5, 16), start)
        assertEquals(LocalDate.of(2026, 6, 15), end)
    }

    @Test
    fun `should calculate credit card dates for closingDay 28`() {
        val (start, end) = calculator.calculateDates(28, "CREDIT_CARD", YearMonth.of(2026, 3))
        // Feb 2026 has 28 days, closingDay=28, clamp=28, start = Feb 28 + 1 = Mar 1
        assertEquals(LocalDate.of(2026, 3, 1), start)
        assertEquals(LocalDate.of(2026, 3, 28), end)
    }

    @Test
    fun `should clamp closingDay 31 for february non-leap year`() {
        val (start, end) = calculator.calculateDates(31, "CREDIT_CARD", YearMonth.of(2026, 3))
        // Previous month is Feb 2026 (28 days), closingDay clamped to 28, start = Feb 28 + 1 = Mar 1
        assertEquals(LocalDate.of(2026, 3, 1), start)
        assertEquals(LocalDate.of(2026, 3, 31), end)
    }

    @Test
    fun `should clamp closingDay 31 for february leap year`() {
        val (start, end) = calculator.calculateDates(31, "CREDIT_CARD", YearMonth.of(2028, 3))
        // Previous month is Feb 2028 (29 days), closingDay clamped to 29, start = Feb 29 + 1 = Mar 1
        assertEquals(LocalDate.of(2028, 3, 1), start)
        assertEquals(LocalDate.of(2028, 3, 31), end)
    }

    @Test
    fun `should calculate credit card dates for closingDay 31 in month with 30 days`() {
        val (start, end) = calculator.calculateDates(31, "CREDIT_CARD", YearMonth.of(2026, 6))
        // Current month June has 30 days, clamp endDate to 30
        assertEquals(LocalDate.of(2026, 6, 1), start) // May has 31, clamp=31, start = May 31 + 1 = Jun 1
        assertEquals(LocalDate.of(2026, 6, 30), end)
    }

    @Test
    fun `should calculate PIX dates as first to last day of month`() {
        val (start, end) = calculator.calculateDates(null, "PIX", YearMonth.of(2026, 5))
        assertEquals(LocalDate.of(2026, 5, 1), start)
        assertEquals(LocalDate.of(2026, 5, 31), end)
    }

    @Test
    fun `should calculate PIX dates for february`() {
        val (start, end) = calculator.calculateDates(null, "PIX", YearMonth.of(2026, 2))
        assertEquals(LocalDate.of(2026, 2, 1), start)
        assertEquals(LocalDate.of(2026, 2, 28), end)
    }

    @Test
    fun `should calculate PIX dates for february leap year`() {
        val (start, end) = calculator.calculateDates(null, "PIX", YearMonth.of(2028, 2))
        assertEquals(LocalDate.of(2028, 2, 1), start)
        assertEquals(LocalDate.of(2028, 2, 29), end)
    }

    @Test
    fun `should calculate credit card dates for closingDay 1`() {
        val (start, end) = calculator.calculateDates(1, "CREDIT_CARD", YearMonth.of(2026, 5))
        // Previous month April, closingDay=1, start = Apr 1 + 1 = Apr 2
        assertEquals(LocalDate.of(2026, 4, 2), start)
        assertEquals(LocalDate.of(2026, 5, 1), end)
    }

    @Test
    fun `should resolve first installment to purchase month when purchase is before closing day`() {
        val dueDate = calculator.resolveInstallmentDueDate(
            purchasedAt = LocalDate.of(2026, 3, 5),
            closingDay = 10,
            type = "CREDIT_CARD",
            installmentNumber = 1,
        )
        assertEquals(LocalDate.of(2026, 3, 5), dueDate)
    }

    @Test
    fun `should resolve first installment to next month when purchase is after closing day`() {
        val dueDate = calculator.resolveInstallmentDueDate(
            purchasedAt = LocalDate.of(2026, 3, 15),
            closingDay = 10,
            type = "CREDIT_CARD",
            installmentNumber = 1,
        )
        assertEquals(LocalDate.of(2026, 4, 15), dueDate)
    }

    @Test
    fun `should resolve first installment to purchase month when purchase equals closing day`() {
        val dueDate = calculator.resolveInstallmentDueDate(
            purchasedAt = LocalDate.of(2026, 3, 10),
            closingDay = 10,
            type = "CREDIT_CARD",
            installmentNumber = 1,
        )
        assertEquals(LocalDate.of(2026, 3, 10), dueDate)
    }

    @Test
    fun `should advance one cycle per subsequent installment`() {
        val purchasedAt = LocalDate.of(2026, 3, 5)
        val secondInstallment = calculator.resolveInstallmentDueDate(purchasedAt, 10, "CREDIT_CARD", 2)
        val thirdInstallment = calculator.resolveInstallmentDueDate(purchasedAt, 10, "CREDIT_CARD", 3)
        assertEquals(LocalDate.of(2026, 4, 5), secondInstallment)
        assertEquals(LocalDate.of(2026, 5, 5), thirdInstallment)
    }

    @Test
    fun `should fallback to calendar month when card has no closing day`() {
        val purchasedAt = LocalDate.of(2026, 3, 20)
        val firstInstallment = calculator.resolveInstallmentDueDate(purchasedAt, null, "CREDIT_CARD", 1)
        val secondInstallment = calculator.resolveInstallmentDueDate(purchasedAt, null, "CREDIT_CARD", 2)
        assertEquals(LocalDate.of(2026, 3, 20), firstInstallment)
        assertEquals(LocalDate.of(2026, 4, 20), secondInstallment)
    }

    @Test
    fun `should fallback to calendar month when payment method is not credit card`() {
        val purchasedAt = LocalDate.of(2026, 3, 20)
        val firstInstallment = calculator.resolveInstallmentDueDate(purchasedAt, 10, "PIX", 1)
        val secondInstallment = calculator.resolveInstallmentDueDate(purchasedAt, 10, "PIX", 2)
        assertEquals(LocalDate.of(2026, 3, 20), firstInstallment)
        assertEquals(LocalDate.of(2026, 4, 20), secondInstallment)
    }

    @Test
    fun `should advance to next cycle when purchase day is after closing day of a short month`() {
        val dueDate = calculator.resolveInstallmentDueDate(
            purchasedAt = LocalDate.of(2026, 1, 31),
            closingDay = 10,
            type = "CREDIT_CARD",
            installmentNumber = 2,
        )
        // first cycle = Feb (day 31 > closingDay 10), installment 2 = Mar
        assertEquals(LocalDate.of(2026, 3, 31), dueDate)
    }

    @Test
    fun `should clamp installment day to february length in leap and non-leap years`() {
        val purchasedAt = LocalDate.of(2026, 1, 31)
        val nonLeapFebruary = calculator.resolveInstallmentDueDate(purchasedAt, null, "CREDIT_CARD", 2)
        assertEquals(LocalDate.of(2026, 2, 28), nonLeapFebruary)

        val leapPurchasedAt = LocalDate.of(2028, 1, 31)
        val leapFebruary = calculator.resolveInstallmentDueDate(leapPurchasedAt, null, "CREDIT_CARD", 2)
        assertEquals(LocalDate.of(2028, 2, 29), leapFebruary)
    }
}
