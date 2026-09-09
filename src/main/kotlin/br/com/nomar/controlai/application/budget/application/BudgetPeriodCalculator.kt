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
            .filter { it.type == "CREDIT_CARD" || it.type == "PIX" || it.type == "CASH" }
            .map { pm ->
                val (startDate, endDate) = calculateDates(yearMonth)
                BudgetPaymentPeriodModel(
                    budget = budget,
                    paymentMethodId = pm.id!!,
                    startDate = startDate,
                    endDate = endDate,
                )
            }
    }

    // Every payment method uses the plain calendar month. Custom cycles (e.g. a card whose
    // real statement doesn't line up with the calendar) are set per month via
    // BudgetPeriodResolver's persisted `budget_payment_periods` override, not computed here.
    fun calculateDates(yearMonth: YearMonth): Pair<LocalDate, LocalDate> {
        return Pair(yearMonth.atDay(1), yearMonth.atEndOfMonth())
    }
}
