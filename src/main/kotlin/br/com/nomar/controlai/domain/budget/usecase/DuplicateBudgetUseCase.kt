package br.com.nomar.controlai.domain.budget.usecase

import br.com.nomar.controlai.domain.budget.entity.Budget
import br.com.nomar.controlai.domain.budget.gateway.DuplicateBudgetGateway
import org.springframework.stereotype.Component
import java.time.YearMonth

@Component
class DuplicateBudgetUseCase(
    private val duplicateBudgetGateway: DuplicateBudgetGateway,
) {
    fun execute(sourceBudgetId: Long, targetYearMonth: YearMonth): Result<Budget> {
        return runCatching {
            duplicateBudgetGateway.execute(sourceBudgetId, targetYearMonth).getOrThrow()
        }
    }
}
