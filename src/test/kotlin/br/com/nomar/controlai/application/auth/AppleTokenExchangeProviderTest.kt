package br.com.nomar.controlai.application.auth

import br.com.nomar.controlai.application.auth.application.AppleTokenExchangeProvider
import br.com.nomar.controlai.domain.auth.exception.InvalidAppleTokenException
import com.nimbusds.jose.crypto.ECDSAVerifier
import com.nimbusds.jwt.SignedJWT
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.mock.http.client.MockClientHttpRequest
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import java.net.URLDecoder
import java.security.KeyPairGenerator
import java.security.interfaces.ECPublicKey
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.Base64
import java.util.Date
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

// The Apple HTTP endpoint is replaced by MockRestServiceServer, so no request leaves the test
class AppleTokenExchangeProviderTest {

    private val keyPair = KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair()
    private val privateKeyPem = toPem(keyPair.private.encoded)

    private val builder: RestClient.Builder = RestClient.builder()
    private val server: MockRestServiceServer = MockRestServiceServer.bindTo(builder).build()

    private var now: Instant = Instant.parse("2026-10-16T12:00:00Z")
    private val clock = object : Clock() {
        override fun getZone() = ZoneOffset.UTC
        override fun withZone(zone: java.time.ZoneId) = this
        override fun instant(): Instant = now
    }

    private val provider = AppleTokenExchangeProvider(
        restClientBuilder = builder,
        teamId = "TEAM123456",
        keyId = "KEY1234567",
        privateKeyPem = privateKeyPem,
        clock = clock,
    )

    @Test
    fun `troca o authorization code e devolve o refresh_token`() {
        server.expect(requestTo("https://appleid.apple.com/auth/token"))
            .andExpect(method(HttpMethod.POST))
            .andExpect { request ->
                val form = formFields((request as MockClientHttpRequest).bodyAsString)
                assertEquals("br.com.nomar.controlai", form["client_id"])
                assertEquals("authorization_code", form["grant_type"])
                assertEquals("auth-code-xyz", form["code"])
            }
            .andRespond(
                withSuccess("""{"access_token":"a","refresh_token":"apple-refresh-123"}""", MediaType.APPLICATION_JSON),
            )

        val refreshToken = provider.exchangeAuthorizationCode("auth-code-xyz", "br.com.nomar.controlai").getOrThrow()

        assertEquals("apple-refresh-123", refreshToken)
        server.verify()
    }

    @Test
    fun `client_secret e um JWT ES256 assinado com a chave da Apple`() {
        server.expect(requestTo("https://appleid.apple.com/auth/token"))
            .andExpect { request ->
                val form = formFields((request as MockClientHttpRequest).bodyAsString)
                val jwt = SignedJWT.parse(form.getValue("client_secret"))

                assertEquals("ES256", jwt.header.algorithm.name)
                assertEquals("KEY1234567", jwt.header.keyID)
                assertEquals("TEAM123456", jwt.jwtClaimsSet.issuer)
                assertEquals("br.com.nomar.controlai", jwt.jwtClaimsSet.subject)
                assertEquals(listOf("https://appleid.apple.com"), jwt.jwtClaimsSet.audience)
                assertTrue(jwt.verify(ECDSAVerifier(keyPair.public as ECPublicKey)))
            }
            .andRespond(withSuccess("""{"refresh_token":"rt"}""", MediaType.APPLICATION_JSON))

        provider.exchangeAuthorizationCode("auth-code", "br.com.nomar.controlai").getOrThrow()

        server.verify()
    }

    @Test
    fun `client_secret expira em cinco minutos e cada chamada gera um JWT novo`() {
        val secrets = mutableListOf<SignedJWT>()
        server.expect(requestTo("https://appleid.apple.com/auth/revoke"))
            .andExpect { request ->
                secrets += SignedJWT.parse(formFields((request as MockClientHttpRequest).bodyAsString).getValue("client_secret"))
            }
            .andRespond(withSuccess())
        server.expect(requestTo("https://appleid.apple.com/auth/revoke"))
            .andExpect { request ->
                secrets += SignedJWT.parse(formFields((request as MockClientHttpRequest).bodyAsString).getValue("client_secret"))
            }
            .andRespond(withSuccess())

        provider.revoke("rt", "br.com.nomar.controlai")
        now = now.plus(Duration.ofMinutes(10))
        provider.revoke("rt", "br.com.nomar.controlai")

        val first = secrets[0].jwtClaimsSet
        assertEquals(Date.from(Instant.parse("2026-10-16T12:00:00Z")), first.issueTime)
        assertEquals(Date.from(Instant.parse("2026-10-16T12:05:00Z")), first.expirationTime)
        assertEquals(Date.from(Instant.parse("2026-10-16T12:10:00Z")), secrets[1].jwtClaimsSet.issueTime)
        assertTrue(secrets[0].serialize() != secrets[1].serialize())
        server.verify()
    }

    @Test
    fun `codigo rejeitado pela Apple devolve falha`() {
        server.expect(requestTo("https://appleid.apple.com/auth/token"))
            .andRespond(withBadRequest().body("""{"error":"invalid_grant"}""").contentType(MediaType.APPLICATION_JSON))

        val result = provider.exchangeAuthorizationCode("used-code", "br.com.nomar.controlai")

        assertTrue(result.isFailure)
    }

    @Test
    fun `resposta sem refresh_token devolve InvalidAppleTokenException`() {
        server.expect(requestTo("https://appleid.apple.com/auth/token"))
            .andRespond(withSuccess("""{"access_token":"a"}""", MediaType.APPLICATION_JSON))

        val result = provider.exchangeAuthorizationCode("auth-code", "br.com.nomar.controlai")

        assertIs<InvalidAppleTokenException>(result.exceptionOrNull())
    }

    @Test
    fun `revoga o refresh_token no endpoint de revogacao com client_secret novo`() {
        server.expect(requestTo("https://appleid.apple.com/auth/revoke"))
            .andExpect(method(HttpMethod.POST))
            .andExpect { request ->
                val form = formFields((request as MockClientHttpRequest).bodyAsString)
                assertEquals("br.com.nomar.controlai", form["client_id"])
                assertEquals("refresh_token", form["token_type_hint"])
                assertEquals("apple-refresh-123", form["token"])
                val jwt = SignedJWT.parse(form.getValue("client_secret"))
                assertEquals("ES256", jwt.header.algorithm.name)
                assertTrue(jwt.verify(ECDSAVerifier(keyPair.public as ECPublicKey)))
            }
            .andRespond(withSuccess())

        assertTrue(provider.revoke("apple-refresh-123", "br.com.nomar.controlai").isSuccess)
        server.verify()
    }

    @Test
    fun `revogacao recusada pela Apple devolve falha`() {
        server.expect(requestTo("https://appleid.apple.com/auth/revoke"))
            .andRespond(withBadRequest().body("""{"error":"invalid_grant"}""").contentType(MediaType.APPLICATION_JSON))

        assertTrue(provider.revoke("apple-refresh-123", "br.com.nomar.controlai").isFailure)
    }

    private fun formFields(body: String): Map<String, String> =
        body.split("&").associate { pair ->
            val (name, value) = pair.split("=", limit = 2)
            URLDecoder.decode(name, Charsets.UTF_8) to URLDecoder.decode(value, Charsets.UTF_8)
        }

    private fun toPem(encoded: ByteArray): String {
        val body = Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(encoded)
        return "-----BEGIN PRIVATE KEY-----\n$body\n-----END PRIVATE KEY-----\n"
    }
}
