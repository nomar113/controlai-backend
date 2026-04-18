package br.com.nomar.controlai.domain.budget.usecase

import br.com.nomar.controlai.domain.budget.entity.Budget
import br.com.nomar.controlai.domain.budget.gateway.FindBudgetGateway
import org.springframework.stereotype.Component
import java.time.YearMonth

@Component
class FindBudgetUseCase(
    private val findBudgetGateway: FindBudgetGateway,
) {
    fun execute(yearMonth: YearMonth): Result<Budget> {
        return runCatching {
            findBudgetGateway.execute(yearMonth).getOrThrow()
        }
    }
}
