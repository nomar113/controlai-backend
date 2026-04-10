package br.com.nomar.controlai.domain.categories.gateway

fun interface CountPurchasesByCategoryGateway {
    fun execute(categoryId: Long): Result<Long>
}
