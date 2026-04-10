package br.com.nomar.controlai.domain.categories.usecase

import br.com.nomar.controlai.domain.categories.entity.Category
import br.com.nomar.controlai.domain.categories.gateway.ListCategoriesGateway
import org.springframework.stereotype.Component

@Component
class ListCategoriesUseCase(
    private val listCategoriesGateway: ListCategoriesGateway,
) {
    fun execute(): Result<List<Category>> {
        return runCatching {
            listCategoriesGateway.execute().getOrThrow()
        }
    }
}
