package br.com.nomar.controlai.domain.categories.gateway

import br.com.nomar.controlai.domain.categories.entity.Category

fun interface FindCategoryGateway {
    fun execute(id: Long): Result<Category>
}
