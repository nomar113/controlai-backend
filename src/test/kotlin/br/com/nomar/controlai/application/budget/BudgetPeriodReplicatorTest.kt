package br.com.nomar.controlai.application.budget

import br.com.nomar.controlai.application.budget.application.BudgetPeriodReplicator
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.assertEquals

class BudgetPeriodReplicatorTest {

    private val replicator = BudgetPeriodReplicator()

    @Test
    fun `should shift credit card period by one month preserving day of month`() {
        val (start, end) = replicator.shift(
            currentYearMonth = YearMonth.of(2026, 5),
            targetYearMonth = YearMonth.of(2026, 6),
            sourceStart = LocalDate.of(2026, 4, 10),
            sourceEnd = LocalDate.of(2026, 5, 9),
        )
        assertEquals(LocalDate.of(2026, 5, 10), start)
        assertEquals(LocalDate.of(2026, 6, 9), end)
    }

    @Test
    fun `should shift period across multiple months`() {
        val (start, end) = replicator.shift(
            currentYearMonth = YearMonth.of(2026, 5),
            targetYearMonth = YearMonth.of(2026, 8),
            sourceStart = LocalDate.of(2026, 4, 10),
            sourceEnd = LocalDate.of(2026, 5, 9),
        )
        assertEquals(LocalDate.of(2026, 7, 10), start)
        assertEquals(LocalDate.of(2026, 8, 9), end)
    }

    @Test
    fun `should clamp day when target month has fewer days`() {
        // Source has day 31; jumping to February which has 28 days in 2026
        val (start, end) = replicator.shift(
            currentYearMonth = YearMonth.of(2026, 1),
            targetYearMonth = YearMonth.of(2026, 2),
            sourceStart = LocalDate.of(2025, 12, 31),
            sourceEnd = LocalDate.of(2026, 1, 31),
        )
        assertEquals(LocalDate.of(2026, 1, 31), start)
        assertEquals(LocalDate.of(2026, 2, 28), end)
    }

    @Test
    fun `should clamp day to leap year February when applicable`() {
        // 2024 is a leap year (29 days in Feb)
        val (start, end) = replicator.shift(
            currentYearMonth = YearMonth.of(2024, 1),
            targetYearMonth = YearMonth.of(2024, 2),
            sourceStart = LocalDate.of(2023, 12, 31),
            sourceEnd = LocalDate.of(2024, 1, 31),
        )
        assertEquals(LocalDate.of(2024, 1, 31), start)
        assertEquals(LocalDate.of(2024, 2, 29), end)
    }

    @Test
    fun `should handle same month period (no cross-month)`() {
        // PIX / cash periods are usually within a single month
        val (start, end) = replicator.shift(
            currentYearMonth = YearMonth.of(2026, 5),
            targetYearMonth = YearMonth.of(2026, 6),
            sourceStart = LocalDate.of(2026, 5, 1),
            sourceEnd = LocalDate.of(2026, 5, 31),
        )
        assertEquals(LocalDate.of(2026, 6, 1), start)
        assertEquals(LocalDate.of(2026, 6, 30), end)
    }

    @Test
    fun `should preserve negative month offset when source predates currentYearMonth`() {
        // Sample: current=2026-05 with start at 2026-03-15 (offset -2 months from current)
        val (start, _) = replicator.shift(
            currentYearMonth = YearMonth.of(2026, 5),
            targetYearMonth = YearMonth.of(2026, 8),
            sourceStart = LocalDate.of(2026, 3, 15),
            sourceEnd = LocalDate.of(2026, 4, 14),
        )
        // Target=2026-08; offset -2 → 2026-06; same day → 2026-06-15
        assertEquals(LocalDate.of(2026, 6, 15), start)
    }
}
