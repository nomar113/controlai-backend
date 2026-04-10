package br.com.nomar.controlai.domain.categories.usecase

import br.com.nomar.controlai.domain.categories.entity.Category
import br.com.nomar.controlai.domain.categories.gateway.SaveCategoryGateway
import org.springframework.stereotype.Component

@Component
class SaveCategoryUseCase(
    private val saveCategoryGateway: SaveCategoryGateway,
) {
    fun execute(category: Category): Result<Category> {
        return runCatching {
            saveCategoryGateway.execute(category).getOrThrow()
        }
    }
}
