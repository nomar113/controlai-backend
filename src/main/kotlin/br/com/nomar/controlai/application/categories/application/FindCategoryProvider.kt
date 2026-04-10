package br.com.nomar.controlai.application.categories.application

import br.com.nomar.controlai.application.categories.converter.CategoryConverter
import br.com.nomar.controlai.application.categories.entrypoint.database.repository.CategoryRepository
import br.com.nomar.controlai.domain.categories.entity.Category
import br.com.nomar.controlai.domain.categories.gateway.FindCategoryGateway
import org.springframework.stereotype.Component

@Component
class FindCategoryProvider(
    private val categoryRepository: CategoryRepository,
    private val converter: CategoryConverter,
) : FindCategoryGateway {

    override fun execute(id: Long): Result<Category> {
        return runCatching {
            val model = categoryRepository.findById(id)
                .orElseThrow { NoSuchElementException("Categoria nao encontrada: $id") }
            converter.toEntity(model)
        }
    }
}
