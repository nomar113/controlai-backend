package br.com.nomar.controlai.application.categories.converter

import br.com.nomar.controlai.application.categories.entrypoint.database.model.CategoryModel
import br.com.nomar.controlai.domain.categories.entity.Category
import org.springframework.stereotype.Component

@Component
class CategoryConverter {

    fun toEntity(model: CategoryModel) = Category(
        id = model.id,
        name = model.name,
    )

    fun toModel(entity: Category) = CategoryModel(
        id = entity.id,
        name = entity.name,
    )
}
