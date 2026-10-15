package br.com.nomar.controlai.application.auth

import br.com.nomar.controlai.application.auth.application.AppleIdentityVerifierProvider
import br.com.nomar.controlai.domain.auth.entity.AppleIdentity
import br.com.nomar.controlai.domain.auth.exception.AppleKeysUnavailableException
import br.com.nomar.controlai.domain.auth.exception.InvalidAppleTokenException
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import com.sun.net.httpserver.HttpServer
import java.net.InetAddress
import java.net.InetSocketAddress
import java.security.MessageDigest
import java.time.Instant
import java.util.Date
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AppleIdentityVerifierProviderTest {

    private val bundleId = "br.com.nomar.controlai"
    private val servicesId = "br.com.nomar.controlai.web"

    private val trustedKey: RSAKey = RSAKeyGenerator(2048).keyID("trusted-kid").generate()
    private val untrustedKey: RSAKey = RSAKeyGenerator(2048).keyID("untrusted-kid").generate()

    // Serves the JWKS locally so the tests never reach appleid.apple.com
    private val jwksServer: HttpServer = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
        .apply {
            createContext("/keys") { exchange ->
                val body = JWKSet(trustedKey.toPublicJWK()).toString(true).toByteArray()
                exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
            start()
        }

    private val verifier = AppleIdentityVerifierProvider(
        rawClientIds = "$bundleId, $servicesId",
        jwkSetUri = "http://127.0.0.1:${jwksServer.address.port}/keys",
    )

    @AfterTest
    fun tearDown() {
        jwksServer.stop(0)
    }

    @Test
    fun `token valido devolve a identidade Apple`() {
        val result = verifier.verify(token(), expectedNonce = null)

        assertEquals(
            AppleIdentity(
                subject = "apple-sub-001",
                email = "user@example.com",
                emailVerified = true,
                isPrivateRelay = false,
                audience = bundleId,
            ),
            result.getOrThrow(),
        )
    }

    @Test
    fun `token com e-mail oculto marca o relay`() {
        val result = verifier.verify(
            token(extraClaims = mapOf("email" to "abc123@privaterelay.appleid.com", "email_verified" to "true", "is_private_email" to "true")),
            expectedNonce = null,
        )

        val identity = result.getOrThrow()
        assertTrue(identity.isPrivateRelay)
        assertTrue(identity.emailVerified)
    }

    @Test
    fun `aceita audience do Services ID da web`() {
        val result = verifier.verify(token(audience = servicesId), expectedNonce = null)

        assertEquals("apple-sub-001", result.getOrThrow().subject)
    }

    @Test
    fun `nonce que bate com o sha256 do valor enviado e aceito`() {
        val result = verifier.verify(token(nonce = sha256Hex("raw-nonce-123")), expectedNonce = "raw-nonce-123")

        assertEquals("apple-sub-001", result.getOrThrow().subject)
    }

    @Test
    fun `nonce divergente e rejeitado`() {
        val result = verifier.verify(token(nonce = sha256Hex("outro-nonce")), expectedNonce = "raw-nonce-123")

        assertRejected(result)
    }

    @Test
    fun `nonce esperado mas ausente no token e rejeitado`() {
        val result = verifier.verify(token(nonce = null), expectedNonce = "raw-nonce-123")

        assertRejected(result)
    }

    @Test
    fun `nonce nao enviado nao e validado`() {
        val result = verifier.verify(token(nonce = sha256Hex("qualquer")), expectedNonce = null)

        assertTrue(result.isSuccess)
    }

    @Test
    fun `issuer incorreto e rejeitado`() {
        assertRejected(verifier.verify(token(issuer = "https://evil.example.com"), expectedNonce = null))
    }

    @Test
    fun `audience que nao e da lista e rejeitada`() {
        assertRejected(verifier.verify(token(audience = "br.com.outro.app"), expectedNonce = null))
    }

    @Test
    fun `token expirado e rejeitado`() {
        assertRejected(verifier.verify(token(expiresAt = Instant.now().minusSeconds(3600)), expectedNonce = null))
    }

    @Test
    fun `token sem exp e rejeitado`() {
        assertRejected(verifier.verify(token(withExpiration = false), expectedNonce = null))
    }

    @Test
    fun `JWKS indisponivel vira erro de indisponibilidade, nao de token invalido`() {
        val unreachable = AppleIdentityVerifierProvider(
            rawClientIds = bundleId,
            jwkSetUri = "http://127.0.0.1:1/keys",
        )

        val result = unreachable.verify(token(), expectedNonce = null)

        assertIs<AppleKeysUnavailableException>(result.exceptionOrNull())
    }

    @Test
    fun `kid desconhecido e rejeitado`() {
        assertRejected(verifier.verify(token(kid = "unknown-kid", signer = untrustedKey), expectedNonce = null))
    }

    @Test
    fun `assinatura de chave nao confiavel com kid conhecido e rejeitada`() {
        assertRejected(verifier.verify(token(kid = trustedKey.keyID, signer = untrustedKey), expectedNonce = null))
    }

    @Test
    fun `texto que nao e JWT e rejeitado`() {
        assertRejected(verifier.verify("nao-e-um-jwt", expectedNonce = null))
    }

    @Test
    fun `email_verified falso e refletido na identidade`() {
        val identity = verifier.verify(
            token(extraClaims = mapOf("email" to "user@example.com", "email_verified" to false)),
            expectedNonce = null,
        ).getOrThrow()

        assertFalse(identity.emailVerified)
    }

    private fun assertRejected(result: Result<AppleIdentity>) {
        assertTrue(result.isFailure)
        assertIs<InvalidAppleTokenException>(result.exceptionOrNull())
    }

    private fun token(
        kid: String = trustedKey.keyID,
        signer: RSAKey = trustedKey,
        issuer: String = "https://appleid.apple.com",
        audience: String = bundleId,
        expiresAt: Instant = Instant.now().plusSeconds(600),
        withExpiration: Boolean = true,
        nonce: String? = null,
        extraClaims: Map<String, Any> = mapOf("email" to "user@example.com", "email_verified" to true),
    ): String {
        val claims = JWTClaimsSet.Builder()
            .issuer(issuer)
            .audience(audience)
            .subject("apple-sub-001")
            .issueTime(Date())
            .apply {
                if (withExpiration) expirationTime(Date.from(expiresAt))
                extraClaims.forEach { (name, value) -> claim(name, value) }
                if (nonce != null) claim("nonce", nonce)
            }
            .build()
        val header = JWSHeader.Builder(JWSAlgorithm.RS256).keyID(kid).build()
        return SignedJWT(header, claims).apply { sign(RSASSASigner(signer)) }.serialize()
    }

    private fun sha256Hex(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
