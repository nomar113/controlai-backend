package br.com.nomar.controlai.domain.auth.gateway

fun interface SeedDefaultCategoriesGateway {
    fun execute(groupId: Long)
}
