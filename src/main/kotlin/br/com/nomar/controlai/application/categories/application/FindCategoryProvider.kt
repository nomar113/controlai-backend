package br.com.nomar.controlai.application.categories.application

import br.com.nomar.controlai.application.categories.converter.CategoryConverter
import br.com.nomar.controlai.application.categories.entrypoint.database.repository.CategoryRepository
import br.com.nomar.controlai.domain.auth.RequestContext
import br.com.nomar.controlai.domain.categories.entity.Category
import br.com.nomar.controlai.domain.categories.gateway.FindCategoryGateway
import org.springframework.stereotype.Component

@Component
class FindCategoryProvider(
    private val categoryRepository: CategoryRepository,
    private val converter: CategoryConverter,
    private val requestContext: RequestContext,
) : FindCategoryGateway {

    override fun execute(id: Long): Result<Category> {
        return runCatching {
            val model = categoryRepository.findByIdAndGroupId(id, requestContext.groupId)
                ?: throw NoSuchElementException("Categoria nao encontrada: $id")
            converter.toEntity(model)
        }
    }
}
