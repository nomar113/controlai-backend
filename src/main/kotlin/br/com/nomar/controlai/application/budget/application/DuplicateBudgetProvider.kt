package br.com.nomar.controlai.application.budget.application

import br.com.nomar.controlai.application.budget.converter.BudgetConverter
import br.com.nomar.controlai.application.budget.entrypoint.database.model.BudgetIncomeModel
import br.com.nomar.controlai.application.budget.entrypoint.database.model.BudgetItemModel
import br.com.nomar.controlai.application.budget.entrypoint.database.model.BudgetModel
import br.com.nomar.controlai.application.budget.entrypoint.database.repository.BudgetRepository
import br.com.nomar.controlai.application.payment_methods.entrypoint.database.repository.PaymentMethodRepository
import br.com.nomar.controlai.domain.auth.RequestContext
import br.com.nomar.controlai.domain.budget.entity.Budget
import br.com.nomar.controlai.domain.budget.gateway.DuplicateBudgetGateway
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.YearMonth

@Component
class DuplicateBudgetProvider(
    private val budgetRepository: BudgetRepository,
    private val converter: BudgetConverter,
    private val paymentMethodRepository: PaymentMethodRepository,
    private val periodCalculator: BudgetPeriodCalculator,
    private val requestContext: RequestContext,
) : DuplicateBudgetGateway {

    private val logger = LoggerFactory.getLogger(DuplicateBudgetProvider::class.java)

    override fun execute(sourceBudgetId: Long, targetYearMonth: YearMonth): Result<Budget> {
        return runCatching {
            val groupId = requestContext.groupId
            val source = budgetRepository.findByIdAndGroupId(sourceBudgetId, groupId)
                ?: throw NoSuchElementException("Source budget not found: $sourceBudgetId")

            if (budgetRepository.findByYearMonthAndGroupId(targetYearMonth.toString(), groupId).isPresent) {
                throw IllegalStateException("Budget for $targetYearMonth already exists.")
            }

            val newBudget = BudgetModel(yearMonth = targetYearMonth.toString(), groupId = groupId)

            newBudget.items.addAll(source.items.map { item ->
                BudgetItemModel(
                    budget = newBudget,
                    categoryId = item.categoryId,
                    type = item.type,
                    expected = item.expected,
                )
            })

            newBudget.incomes.addAll(source.incomes.map { income ->
                BudgetIncomeModel(
                    budget = newBudget,
                    label = income.label,
                    amount = income.amount,
                )
            })

            val paymentMethods = paymentMethodRepository.findAllByGroupIdOrderByNameAsc(groupId)
            val periods = periodCalculator.generatePeriods(newBudget, paymentMethods, targetYearMonth)
            newBudget.paymentPeriods.addAll(periods)
            logger.info("Generated ${periods.size} payment periods for duplicated budget $targetYearMonth")

            val saved = budgetRepository.save(newBudget)
            converter.toEntity(saved)
        }
    }
}
