package br.com.nomar.controlai.domain.auth.gateway

interface AppleTokenExchangeGateway {
    // Returns Apple's refresh_token; clientId is the bundle ID or Services ID that issued the identityToken
    fun exchangeAuthorizationCode(authorizationCode: String, clientId: String): Result<String>

    // clientId must be the one that issued the refresh_token, otherwise Apple refuses the revocation
    fun revoke(refreshToken: String, clientId: String): Result<Unit>
}
