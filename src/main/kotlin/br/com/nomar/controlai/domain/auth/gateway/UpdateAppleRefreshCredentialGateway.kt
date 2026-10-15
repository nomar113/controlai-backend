package br.com.nomar.controlai.domain.auth.gateway

// Replaces the encrypted Apple credential of the user; null clears it once Apple has revoked the token
fun interface UpdateAppleRefreshCredentialGateway {
    fun execute(userId: Long, encryptedCredential: String?): Result<Unit>
}
