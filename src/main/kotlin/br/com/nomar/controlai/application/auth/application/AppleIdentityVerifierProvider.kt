package br.com.nomar.controlai.application.auth.application

import br.com.nomar.controlai.domain.auth.entity.AppleIdentity
import br.com.nomar.controlai.domain.auth.exception.AppleKeysUnavailableException
import br.com.nomar.controlai.domain.auth.exception.InvalidAppleTokenException
import br.com.nomar.controlai.domain.auth.gateway.AppleIdentityVerifierGateway
import com.nimbusds.jose.RemoteKeySourceException
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.oauth2.core.OAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtException
import org.springframework.security.oauth2.jwt.JwtValidators
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.stereotype.Component
import java.security.MessageDigest

@Component
class AppleIdentityVerifierProvider(
    @Value("\${apple.client-ids}") rawClientIds: String,
    @Value("\${apple.jwk-set-uri:https://appleid.apple.com/auth/keys}") jwkSetUri: String,
) : AppleIdentityVerifierGateway {

    private val clientIds: Set<String> = rawClientIds.split(",").map(String::trim).filter(String::isNotEmpty).toSet()

    private val decoder: NimbusJwtDecoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build().apply {
        setJwtValidator(
            DelegatingOAuth2TokenValidator(
                JwtValidators.createDefaultWithIssuer(APPLE_ISSUER),
                OAuth2TokenValidator { jwt -> validateExpirationPresent(jwt) },
                OAuth2TokenValidator { jwt -> validateAudience(jwt) },
            ),
        )
    }

    override fun verify(identityToken: String, expectedNonce: String?): Result<AppleIdentity> = runCatching {
        val jwt = try {
            decoder.decode(identityToken)
        } catch (ex: JwtException) {
            // Falha ao buscar as chaves da Apple é indisponibilidade, não token inválido
            if (ex.cause is RemoteKeySourceException) throw AppleKeysUnavailableException(ex)
            throw InvalidAppleTokenException()
        }

        if (expectedNonce != null && jwt.getClaimAsString(CLAIM_NONCE) != sha256Hex(expectedNonce)) {
            throw InvalidAppleTokenException()
        }

        val subject = jwt.subject?.takeIf(String::isNotBlank) ?: throw InvalidAppleTokenException()
        AppleIdentity(
            subject = subject,
            email = jwt.getClaimAsString(CLAIM_EMAIL),
            emailVerified = jwt.booleanClaim(CLAIM_EMAIL_VERIFIED),
            isPrivateRelay = jwt.booleanClaim(CLAIM_IS_PRIVATE_EMAIL),
            audience = jwt.audience.first(clientIds::contains),
        )
    }

    // Sem exp o JwtTimestampValidator não checa a validade, então o token seria aceito para sempre
    private fun validateExpirationPresent(jwt: Jwt): OAuth2TokenValidatorResult =
        if (jwt.expiresAt != null) {
            OAuth2TokenValidatorResult.success()
        } else {
            OAuth2TokenValidatorResult.failure(OAuth2Error("invalid_token", "Token da Apple sem exp", null))
        }

    private fun validateAudience(jwt: Jwt): OAuth2TokenValidatorResult =
        if (jwt.audience.any(clientIds::contains)) {
            OAuth2TokenValidatorResult.success()
        } else {
            OAuth2TokenValidatorResult.failure(OAuth2Error("invalid_token", "Audience da Apple invalida", null))
        }

    // Apple's `email_verified` and `is_private_email` may arrive as booleans or as strings
    private fun Jwt.booleanClaim(name: String): Boolean = when (val value = getClaim<Any?>(name)) {
        is Boolean -> value
        is String -> value.toBoolean()
        else -> false
    }

    // The app sends the raw nonce; Apple embeds its SHA-256 hex digest in the token
    private fun sha256Hex(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private companion object {
        const val APPLE_ISSUER = "https://appleid.apple.com"
        const val CLAIM_NONCE = "nonce"
        const val CLAIM_EMAIL = "email"
        const val CLAIM_EMAIL_VERIFIED = "email_verified"
        const val CLAIM_IS_PRIVATE_EMAIL = "is_private_email"
    }
}
