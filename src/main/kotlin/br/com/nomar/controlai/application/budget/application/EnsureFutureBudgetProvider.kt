package br.com.nomar.controlai.application.budget.application

import br.com.nomar.controlai.application.budget.entrypoint.database.model.BudgetModel
import br.com.nomar.controlai.application.budget.entrypoint.database.repository.BudgetRepository
import br.com.nomar.controlai.application.payment_methods.entrypoint.database.repository.PaymentMethodRepository
import br.com.nomar.controlai.domain.budget.gateway.EnsureFutureBudgetGateway
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.YearMonth

@Component
class EnsureFutureBudgetProvider(
    private val budgetRepository: BudgetRepository,
    private val paymentMethodRepository: PaymentMethodRepository,
    private val periodCalculator: BudgetPeriodCalculator,
) : EnsureFutureBudgetGateway {

    private val logger = LoggerFactory.getLogger(EnsureFutureBudgetProvider::class.java)

    @Transactional
    override fun execute(groupId: Long, yearMonth: YearMonth): Result<Long> {
        return runCatching {
            budgetRepository.findByYearMonthAndGroupId(yearMonth.toString(), groupId)
                .map { it.id!! }
                .orElseGet { createBudget(groupId, yearMonth) }
        }
    }

    private fun createBudget(groupId: Long, yearMonth: YearMonth): Long {
        val latest = budgetRepository.findFirstByGroupIdOrderByYearMonthDesc(groupId)
        val newBudget = BudgetModel(yearMonth = yearMonth.toString(), groupId = groupId)

        if (latest != null) {
            BudgetCopySupport.copyItemsAndIncomes(latest, newBudget)
        }

        val paymentMethods = paymentMethodRepository.findAllByGroupIdOrderByNameAsc(groupId)
        val periods = periodCalculator.generatePeriods(newBudget, paymentMethods, yearMonth)
        newBudget.paymentPeriods.addAll(periods)

        val saved = budgetRepository.save(newBudget)
        logger.info(
            "Auto-created future budget for group $groupId / $yearMonth " +
                "(source=${latest?.yearMonth ?: "none, empty budget"}, ${periods.size} payment periods)"
        )
        return saved.id!!
    }
}
