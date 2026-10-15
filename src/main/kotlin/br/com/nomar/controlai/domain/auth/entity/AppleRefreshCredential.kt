package br.com.nomar.controlai.domain.auth.entity

// What is stored (encrypted) in user_identities.apple_refresh_token_enc. Apple only revokes a
// refresh token for the client_id that issued it, so the client_id travels with the token.
data class AppleRefreshCredential(
    val clientId: String,
    val refreshToken: String,
) {

    fun serialize(): String = "$clientId$SEPARATOR$refreshToken"

    companion object {
        // Neither a bundle ID nor a Services ID contains a line break, so the first one splits the value
        private const val SEPARATOR = "\n"

        fun parse(value: String): AppleRefreshCredential {
            val (clientId, refreshToken) = value.split(SEPARATOR, limit = 2)
                .takeIf { it.size == 2 && it[0].isNotBlank() && it[1].isNotBlank() }
                ?: throw IllegalArgumentException("Credencial Apple armazenada em formato invalido")
            return AppleRefreshCredential(clientId, refreshToken)
        }
    }
}
