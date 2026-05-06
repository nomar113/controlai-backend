package br.com.nomar.controlai.application.budget.application

import br.com.nomar.controlai.application.budget.entrypoint.database.model.BudgetModel
import br.com.nomar.controlai.application.budget.entrypoint.database.model.BudgetPaymentPeriodModel
import br.com.nomar.controlai.application.payment_methods.entrypoint.database.model.PaymentMethodModel
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.YearMonth

@Component
class BudgetPeriodCalculator {

    fun generatePeriods(
        budget: BudgetModel,
        paymentMethods: List<PaymentMethodModel>,
        yearMonth: YearMonth,
    ): List<BudgetPaymentPeriodModel> {
        return paymentMethods
            .filter { it.type == "CREDIT_CARD" || it.type == "PIX" }
            .map { pm ->
                val (startDate, endDate) = calculateDates(pm.closingDay, pm.type, yearMonth)
                BudgetPaymentPeriodModel(
                    budget = budget,
                    paymentMethodId = pm.id!!,
                    startDate = startDate,
                    endDate = endDate,
                )
            }
    }

    fun calculateDates(closingDay: Int?, type: String, yearMonth: YearMonth): Pair<LocalDate, LocalDate> {
        return if (type == "CREDIT_CARD" && closingDay != null) {
            calculateCreditCardDates(closingDay, yearMonth)
        } else {
            val startDate = yearMonth.atDay(1)
            val endDate = yearMonth.atEndOfMonth()
            Pair(startDate, endDate)
        }
    }

    private fun calculateCreditCardDates(closingDay: Int, yearMonth: YearMonth): Pair<LocalDate, LocalDate> {
        val previousMonth = yearMonth.minusMonths(1)
        val clampedClosingDayPrev = Math.min(closingDay, previousMonth.lengthOfMonth())
        val clampedClosingDayCurr = Math.min(closingDay, yearMonth.lengthOfMonth())

        val startDate = previousMonth.atDay(clampedClosingDayPrev).plusDays(1)
        val endDate = yearMonth.atDay(clampedClosingDayCurr)

        return Pair(startDate, endDate)
    }
}
