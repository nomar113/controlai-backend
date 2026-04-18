package br.com.nomar.controlai.application.categories.entrypoint.rest.response

import br.com.nomar.controlai.domain.categories.entity.Category

data class CategoryResponse(
    val id: Long?,
    val name: String,
    val icon: String?,
) {
    companion object {
        fun from(entity: Category) = CategoryResponse(
            id = entity.id,
            name = entity.name,
            icon = entity.icon,
        )
    }
}
