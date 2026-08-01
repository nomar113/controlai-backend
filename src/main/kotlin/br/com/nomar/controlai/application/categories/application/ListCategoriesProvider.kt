package br.com.nomar.controlai.application.categories.application

import br.com.nomar.controlai.application.categories.converter.CategoryConverter
import br.com.nomar.controlai.application.categories.entrypoint.database.repository.CategoryRepository
import br.com.nomar.controlai.domain.auth.RequestContext
import br.com.nomar.controlai.domain.categories.entity.Category
import br.com.nomar.controlai.domain.categories.gateway.ListCategoriesGateway
import org.springframework.stereotype.Component

@Component
class ListCategoriesProvider(
    private val categoryRepository: CategoryRepository,
    private val converter: CategoryConverter,
    private val requestContext: RequestContext,
) : ListCategoriesGateway {

    override fun execute(): Result<List<Category>> {
        return runCatching {
            categoryRepository.findAllByGroupIdOrderByNameAsc(requestContext.groupId)
                .map(converter::toEntity)
        }
    }
}
