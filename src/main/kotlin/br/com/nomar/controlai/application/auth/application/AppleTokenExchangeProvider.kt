package br.com.nomar.controlai.application.auth.application

import br.com.nomar.controlai.domain.auth.exception.InvalidAppleTokenException
import br.com.nomar.controlai.domain.auth.gateway.AppleTokenExchangeGateway
import com.fasterxml.jackson.annotation.JsonProperty
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.ECDSASigner
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient
import java.security.KeyFactory
import java.security.interfaces.ECPrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import java.time.Clock
import java.util.Base64
import java.util.Date

@Component
class AppleTokenExchangeProvider(
    restClientBuilder: RestClient.Builder,
    @Value("\${apple.team-id}") private val teamId: String,
    @Value("\${apple.key-id}") private val keyId: String,
    @Value("\${apple.private-key}") private val privateKeyPem: String,
    private val clock: Clock,
) : AppleTokenExchangeGateway {

    private val restClient: RestClient = restClientBuilder.baseUrl(APPLE_BASE_URL).build()

    // Parsed on first use so the application starts without the .p8 key, e.g. in environments without Apple login
    private val signingKey: ECPrivateKey by lazy { parsePkcs8EcKey(privateKeyPem) }

    override fun exchangeAuthorizationCode(authorizationCode: String, clientId: String): Result<String> {
        return runCatching {
            val form = LinkedMultiValueMap<String, String>().apply {
                add("client_id", clientId)
                add("client_secret", buildClientSecret(clientId))
                add("grant_type", "authorization_code")
                add("code", authorizationCode)
            }
            val response = restClient.post()
                .uri(TOKEN_PATH)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(AppleTokenResponse::class.java)

            response?.refreshToken?.takeIf(String::isNotBlank) ?: throw InvalidAppleTokenException()
        }
    }

    override fun revoke(refreshToken: String, clientId: String): Result<Unit> {
        return runCatching {
            val form = LinkedMultiValueMap<String, String>().apply {
                add("client_id", clientId)
                add("client_secret", buildClientSecret(clientId))
                add("token", refreshToken)
                add("token_type_hint", "refresh_token")
            }
            restClient.post()
                .uri(REVOKE_PATH)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .toBodilessEntity()
            Unit
        }
    }

    // The Apple client_secret is a JWT signed with ES256; it is generated per call with a short lifetime
    private fun buildClientSecret(clientId: String): String {
        val now = Date.from(clock.instant())
        val header = JWSHeader.Builder(JWSAlgorithm.ES256).keyID(keyId).build()
        val claims = JWTClaimsSet.Builder()
            .issuer(teamId)
            .subject(clientId)
            .audience(APPLE_BASE_URL)
            .issueTime(now)
            .expirationTime(Date(now.time + CLIENT_SECRET_TTL_MILLIS))
            .build()
        return SignedJWT(header, claims).apply { sign(ECDSASigner(signingKey)) }.serialize()
    }

    // Accepts the PEM as stored in env vars, where line breaks may come escaped as "\n"
    private fun parsePkcs8EcKey(pem: String): ECPrivateKey {
        val body = pem.replace("\\n", "\n")
            .lines()
            .filterNot { it.startsWith("-----") }
            .joinToString("")
        val keySpec = PKCS8EncodedKeySpec(Base64.getDecoder().decode(body))
        return KeyFactory.getInstance("EC").generatePrivate(keySpec) as ECPrivateKey
    }

    private data class AppleTokenResponse(
        @JsonProperty("refresh_token") val refreshToken: String?,
    )

    private companion object {
        const val APPLE_BASE_URL = "https://appleid.apple.com"
        const val TOKEN_PATH = "/auth/token"
        const val REVOKE_PATH = "/auth/revoke"
        const val CLIENT_SECRET_TTL_MILLIS = 5 * 60 * 1000L
    }
}
