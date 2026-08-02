package br.com.nomar.controlai.domain.auth.gateway

interface EmailGateway {
    fun sendPasswordReset(toEmail: String, toName: String, resetLink: String): Result<Unit>
    fun sendGroupInvite(toEmail: String, inviteLink: String): Result<Unit>
}
