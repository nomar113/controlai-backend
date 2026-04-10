package br.com.nomar.controlai.application.categories.application

import br.com.nomar.controlai.application.categories.converter.CategoryConverter
import br.com.nomar.controlai.application.categories.entrypoint.database.repository.CategoryRepository
import br.com.nomar.controlai.domain.categories.entity.Category
import br.com.nomar.controlai.domain.categories.gateway.UpdateCategoryGateway
import org.springframework.stereotype.Component

@Component
class UpdateCategoryProvider(
    private val categoryRepository: CategoryRepository,
    private val converter: CategoryConverter,
) : UpdateCategoryGateway {

    override fun execute(category: Category): Result<Category> {
        return runCatching {
            val existing = categoryRepository.findById(category.id!!)
                .orElseThrow { NoSuchElementException("Categoria nao encontrada: ${category.id}") }
            val updated = existing.copy(name = category.name)
            val saved = categoryRepository.save(updated)
            converter.toEntity(saved)
        }
    }
}
