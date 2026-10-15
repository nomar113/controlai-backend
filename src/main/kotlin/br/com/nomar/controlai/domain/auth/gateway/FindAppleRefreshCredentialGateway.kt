package br.com.nomar.controlai.domain.auth.gateway

// Returns the encrypted Apple credential of the user, or null when the user has no Apple identity
fun interface FindAppleRefreshCredentialGateway {
    fun execute(userId: Long): Result<String?>
}
