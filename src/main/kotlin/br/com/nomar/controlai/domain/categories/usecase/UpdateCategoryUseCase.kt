package br.com.nomar.controlai.domain.categories.usecase

import br.com.nomar.controlai.domain.categories.entity.Category
import br.com.nomar.controlai.domain.categories.gateway.UpdateCategoryGateway
import org.springframework.stereotype.Component

@Component
class UpdateCategoryUseCase(
    private val updateCategoryGateway: UpdateCategoryGateway,
) {
    fun execute(category: Category): Result<Category> {
        return runCatching {
            updateCategoryGateway.execute(category).getOrThrow()
        }
    }
}
