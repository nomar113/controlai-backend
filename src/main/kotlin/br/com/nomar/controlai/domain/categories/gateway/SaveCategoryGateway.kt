package br.com.nomar.controlai.domain.categories.gateway

import br.com.nomar.controlai.domain.categories.entity.Category

fun interface SaveCategoryGateway {
    fun execute(category: Category): Result<Category>
}
