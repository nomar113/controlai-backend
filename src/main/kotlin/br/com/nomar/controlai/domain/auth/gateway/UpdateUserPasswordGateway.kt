package br.com.nomar.controlai.domain.auth.gateway

fun interface UpdateUserPasswordGateway {
    fun execute(userId: Long, newPasswordHash: String): Result<Unit>
}
