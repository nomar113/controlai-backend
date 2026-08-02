package br.com.nomar.controlai.domain.auth.gateway

fun interface EmailGateway {
    fun sendPasswordReset(toEmail: String, toName: String, resetLink: String): Result<Unit>
}
