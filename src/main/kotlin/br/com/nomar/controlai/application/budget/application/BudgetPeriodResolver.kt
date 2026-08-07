package br.com.nomar.controlai.application.budget.application

import br.com.nomar.controlai.application.budget.entrypoint.database.model.BudgetModel
import br.com.nomar.controlai.application.budget.entrypoint.database.repository.BudgetRepository
import br.com.nomar.controlai.application.payment_methods.entrypoint.database.repository.PaymentMethodRepository
import br.com.nomar.controlai.domain.auth.RequestContext
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.YearMonth

data class ResolvedPeriod(
    val paymentMethodId: Long,
    val startDate: LocalDate,
    val endDate: LocalDate,
)

@Component
class BudgetPeriodResolver(
    private val budgetRepository: BudgetRepository,
    private val paymentMethodRepository: PaymentMethodRepository,
    private val periodCalculator: BudgetPeriodCalculator,
    private val requestContext: RequestContext,
) {

    private val logger = LoggerFactory.getLogger(BudgetPeriodResolver::class.java)

    /**
     * Resolves the invoice-period date range per payment method for [yearMonth], without
     * ever creating a Budget: reads the persisted periods when a budget already exists for
     * that month (self-healing missing payment methods), otherwise computes them in memory.
     */
    @Transactional
    fun resolvePeriods(yearMonth: YearMonth): List<ResolvedPeriod> {
        val groupId = requestContext.groupId
        val budget = budgetRepository.findByYearMonthAndGroupId(yearMonth.toString(), groupId).orElse(null)

        if (budget != null) {
            ensurePaymentPeriodsSynced(budget, yearMonth)
            return budget.paymentPeriods.map {
                ResolvedPeriod(it.paymentMethodId, it.startDate, it.endDate)
            }
        }

        val paymentMethods = paymentMethodRepository.findAllByGroupIdOrderByNameAsc(groupId)
        return paymentMethods
            .filter { it.type == "CREDIT_CARD" || it.type == "PIX" || it.type == "CASH" }
            .map { pm ->
                val (startDate, endDate) = periodCalculator.calculateDates(pm.closingDay, pm.type, yearMonth)
                ResolvedPeriod(pm.id!!, startDate, endDate)
            }
    }

    /**
     * Self-heals an existing budget's payment periods when a payment method was added after
     * the budget was created. Never creates the budget itself.
     */
    @Transactional
    fun ensurePaymentPeriodsSynced(budget: BudgetModel, yearMonth: YearMonth) {
        val groupId = requestContext.groupId
        val paymentMethods = paymentMethodRepository.findAllByGroupIdOrderByNameAsc(groupId)
        val existingMethodIds = budget.paymentPeriods.map { it.paymentMethodId }.toSet()
        val missingMethods = paymentMethods.filter { it.id!! !in existingMethodIds }

        if (missingMethods.isNotEmpty()) {
            val periods = periodCalculator.generatePeriods(budget, missingMethods, yearMonth)
            budget.paymentPeriods.addAll(periods)
            budgetRepository.save(budget)
            logger.info("Created ${periods.size} payment periods for budget $yearMonth")
        }
    }
}
