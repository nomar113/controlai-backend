package br.com.nomar.controlai.domain.budget.usecase

import br.com.nomar.controlai.domain.budget.entity.BudgetSummary
import br.com.nomar.controlai.domain.budget.gateway.GetBudgetSummaryGateway
import org.springframework.stereotype.Component
import java.time.YearMonth

@Component
class GetBudgetSummaryUseCase(
    private val getBudgetSummaryGateway: GetBudgetSummaryGateway,
) {
    fun execute(yearMonth: YearMonth): Result<BudgetSummary> {
        return runCatching {
            getBudgetSummaryGateway.execute(yearMonth).getOrThrow()
        }
    }
}
