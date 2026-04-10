package br.com.nomar.controlai.domain.categories.usecase

import br.com.nomar.controlai.domain.categories.entity.Category
import br.com.nomar.controlai.domain.categories.gateway.FindCategoryGateway
import org.springframework.stereotype.Component

@Component
class FindCategoryUseCase(
    private val findCategoryGateway: FindCategoryGateway,
) {
    fun execute(id: Long): Result<Category> {
        return runCatching {
            findCategoryGateway.execute(id).getOrThrow()
        }
    }
}
