package br.com.nomar.controlai.application.budget.application

import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlin.math.min

@Component
class BudgetPeriodReplicator {

    fun shift(
        currentYearMonth: YearMonth,
        targetYearMonth: YearMonth,
        sourceStart: LocalDate,
        sourceEnd: LocalDate,
    ): Pair<LocalDate, LocalDate> {
        val startMonthOffset = ChronoUnit.MONTHS.between(currentYearMonth, YearMonth.from(sourceStart))
        val endMonthOffset = ChronoUnit.MONTHS.between(currentYearMonth, YearMonth.from(sourceEnd))
        val newStartYm = targetYearMonth.plusMonths(startMonthOffset)
        val newEndYm = targetYearMonth.plusMonths(endMonthOffset)
        val newStart = newStartYm.atDay(min(sourceStart.dayOfMonth, newStartYm.lengthOfMonth()))
        val newEnd = newEndYm.atDay(min(sourceEnd.dayOfMonth, newEndYm.lengthOfMonth()))
        return newStart to newEnd
    }
}
