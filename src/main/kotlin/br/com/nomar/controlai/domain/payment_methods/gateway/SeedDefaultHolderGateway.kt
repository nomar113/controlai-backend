package br.com.nomar.controlai.domain.payment_methods.gateway

fun interface SeedDefaultHolderGateway {
    fun execute(groupId: Long, holderName: String)
}
