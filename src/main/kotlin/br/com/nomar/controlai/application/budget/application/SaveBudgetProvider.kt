package br.com.nomar.controlai.application.budget.application

import br.com.nomar.controlai.application.budget.converter.BudgetConverter
import br.com.nomar.controlai.application.budget.entrypoint.database.repository.BudgetRepository
import br.com.nomar.controlai.application.payment_methods.entrypoint.database.repository.PaymentMethodRepository
import br.com.nomar.controlai.domain.auth.RequestContext
import br.com.nomar.controlai.domain.budget.entity.Budget
import br.com.nomar.controlai.domain.budget.gateway.SaveBudgetGateway
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class SaveBudgetProvider(
    private val budgetRepository: BudgetRepository,
    private val converter: BudgetConverter,
    private val paymentMethodRepository: PaymentMethodRepository,
    private val periodCalculator: BudgetPeriodCalculator,
    private val requestContext: RequestContext,
) : SaveBudgetGateway {

    private val logger = LoggerFactory.getLogger(SaveBudgetProvider::class.java)

    override fun execute(budget: Budget): Result<Budget> {
        return runCatching {
            val groupId = requestContext.groupId
            val model = converter.toModel(budget).copy(groupId = groupId)
            val paymentMethods = paymentMethodRepository.findAllByGroupIdOrderByNameAsc(groupId)
            val periods = periodCalculator.generatePeriods(model, paymentMethods, budget.yearMonth)
            model.paymentPeriods.addAll(periods)
            logger.info("Generated ${periods.size} payment periods for budget ${budget.yearMonth}")
            val saved = budgetRepository.save(model)
            converter.toEntity(saved)
        }
    }
}
