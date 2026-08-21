package br.com.nomar.controlai.application.budget.application

import br.com.nomar.controlai.application.budget.entrypoint.database.model.BudgetModel
import br.com.nomar.controlai.application.budget.entrypoint.database.repository.BudgetRepository
import br.com.nomar.controlai.application.payment_methods.entrypoint.database.repository.PaymentMethodRepository
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
) {

    private val logger = LoggerFactory.getLogger(BudgetPeriodResolver::class.java)

    /**
     * Resolves the invoice-period date range per payment method for [yearMonth], without
     * ever creating a Budget: reads the persisted periods when a budget already exists for
     * that month (self-healing missing payment methods), otherwise computes them in memory.
     *
     * [groupId] is mandatory (no RequestContext default): a Kotlin default parameter is
     * evaluated by a synthetic bridge method that touches the real field on `this`, which
     * blows up with a NullPointerException when the caller holds a Mockito mock instead of a
     * real bean (mocks skip the constructor, so injected fields like a RequestContext are never
     * set) — every caller, request-scoped or not, must pass it explicitly.
     */
    @Transactional
    fun resolvePeriods(yearMonth: YearMonth, groupId: Long): List<ResolvedPeriod> {
        val budget = budgetRepository.findByYearMonthAndGroupId(yearMonth.toString(), groupId).orElse(null)

        if (budget != null) {
            ensurePaymentPeriodsSynced(budget, yearMonth, groupId)
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
     * Determines which invoice month a purchase belongs to and, from there, the due date of one
     * of its installments. Prefers an already-persisted budget_payment_periods row (which may
     * have been manually edited via UpdateBudgetPeriodsProvider, e.g. to correct a card's real
     * closing date) over a fresh closingDay-based calculation — so once a month's period is set,
     * installment placement stays consistent with what "Periodo por meio de pagamento" displays
     * instead of silently diverging from it. Falls back to the fresh calculation (via
     * [resolvePeriods]) when no budget/period exists yet for the purchase's own month, and to a
     * plain calendar month when [paymentMethodId] is null (no card selected).
     */
    @Transactional
    fun resolveInstallmentDueDate(
        purchasedAt: LocalDate,
        paymentMethodId: Long?,
        installmentNumber: Int,
        groupId: Long,
    ): LocalDate {
        val firstInvoiceMonth = paymentMethodId?.let { resolveInvoiceMonth(purchasedAt, it, groupId) } ?: YearMonth.from(purchasedAt)
        val targetMonth = firstInvoiceMonth.plusMonths((installmentNumber - 1).toLong())
        val dayOfMonth = minOf(purchasedAt.dayOfMonth, targetMonth.lengthOfMonth())
        return targetMonth.atDay(dayOfMonth)
    }

    private fun resolveInvoiceMonth(purchasedAt: LocalDate, paymentMethodId: Long, groupId: Long): YearMonth {
        val purchaseMonth = YearMonth.from(purchasedAt)
        val period = resolvePeriods(purchaseMonth, groupId).find { it.paymentMethodId == paymentMethodId }
            ?: return purchaseMonth
        return if (purchasedAt <= period.endDate) purchaseMonth else purchaseMonth.plusMonths(1)
    }

    /**
     * Self-heals an existing budget's payment periods when a payment method was added after
     * the budget was created. Never creates the budget itself.
     */
    @Transactional
    fun ensurePaymentPeriodsSynced(budget: BudgetModel, yearMonth: YearMonth, groupId: Long) {
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
