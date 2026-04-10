package br.com.nomar.controlai.domain.categories.gateway

import br.com.nomar.controlai.domain.categories.entity.Category

fun interface ListCategoriesGateway {
    fun execute(): Result<List<Category>>
}
