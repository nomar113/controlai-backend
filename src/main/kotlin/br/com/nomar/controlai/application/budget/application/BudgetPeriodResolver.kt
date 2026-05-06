package br.com.nomar.controlai.application.budget.application

import br.com.nomar.controlai.application.budget.entrypoint.database.model.BudgetModel
import br.com.nomar.controlai.application.budget.entrypoint.database.repository.BudgetRepository
import br.com.nomar.controlai.application.payment_methods.entrypoint.database.repository.PaymentMethodRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.YearMonth

@Component
class BudgetPeriodResolver(
    private val budgetRepository: BudgetRepository,
    private val paymentMethodRepository: PaymentMethodRepository,
    private val periodCalculator: BudgetPeriodCalculator,
) {

    private val logger = LoggerFactory.getLogger(BudgetPeriodResolver::class.java)

    @Transactional
    fun resolveBudgetId(yearMonth: YearMonth): Long {
        val budget = budgetRepository.findByYearMonth(yearMonth.toString())
            .orElseGet {
                budgetRepository.save(BudgetModel(yearMonth = yearMonth.toString()))
            }

        if (budget.paymentPeriods.isEmpty()) {
            val paymentMethods = paymentMethodRepository.findAllByOrderByNameAsc()
            val periods = periodCalculator.generatePeriods(budget, paymentMethods, yearMonth)
            budget.paymentPeriods.addAll(periods)
            budgetRepository.save(budget)
            logger.info("Lazy-created ${periods.size} payment periods for budget $yearMonth")
        }

        return budget.id!!
    }
}
