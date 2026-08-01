package br.com.nomar.controlai.application.budget.application

import br.com.nomar.controlai.application.budget.entrypoint.database.model.BudgetModel
import br.com.nomar.controlai.application.budget.entrypoint.database.repository.BudgetRepository
import br.com.nomar.controlai.application.payment_methods.entrypoint.database.repository.PaymentMethodRepository
import br.com.nomar.controlai.domain.auth.RequestContext
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.YearMonth

@Component
class BudgetPeriodResolver(
    private val budgetRepository: BudgetRepository,
    private val paymentMethodRepository: PaymentMethodRepository,
    private val periodCalculator: BudgetPeriodCalculator,
    private val requestContext: RequestContext,
) {

    private val logger = LoggerFactory.getLogger(BudgetPeriodResolver::class.java)

    @Transactional
    fun resolveBudgetId(yearMonth: YearMonth): Long {
        val groupId = requestContext.groupId
        val budget = budgetRepository.findByYearMonthAndGroupId(yearMonth.toString(), groupId)
            .orElseGet {
                budgetRepository.save(BudgetModel(yearMonth = yearMonth.toString(), groupId = groupId))
            }

        val paymentMethods = paymentMethodRepository.findAllByGroupIdOrderByNameAsc(groupId)
        val existingMethodIds = budget.paymentPeriods.map { it.paymentMethodId }.toSet()
        val missingMethods = paymentMethods.filter { it.id!! !in existingMethodIds }

        if (missingMethods.isNotEmpty()) {
            val periods = periodCalculator.generatePeriods(budget, missingMethods, yearMonth)
            budget.paymentPeriods.addAll(periods)
            budgetRepository.save(budget)
            logger.info("Created ${periods.size} payment periods for budget $yearMonth")
        }

        return budget.id!!
    }
}
