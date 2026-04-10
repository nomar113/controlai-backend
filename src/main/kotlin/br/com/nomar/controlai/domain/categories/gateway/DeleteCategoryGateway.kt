package br.com.nomar.controlai.domain.categories.gateway

fun interface DeleteCategoryGateway {
    fun execute(id: Long): Result<Unit>
}
