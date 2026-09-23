package br.com.nomar.controlai.config

import br.com.nomar.controlai.domain.account_deletion.gateway.FindDeletionScheduleByUserIdGateway
import br.com.nomar.controlai.domain.account_deletion.gateway.FindDeletionSchedulesByGroupIdGateway
import br.com.nomar.controlai.domain.account_deletion.usecase.ResolveGroupDeletionBlockUseCase
import br.com.nomar.controlai.domain.account_deletion.usecase.ResolveUserDeletionBlockUseCase
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import jakarta.servlet.FilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import java.time.Instant
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AccountDeletionGuardFilterTest {

    private val scheduledFor = Instant.parse("2026-10-23T15:00:00Z")
    private val meterRegistry = SimpleMeterRegistry()
    private var chainCalled = false
    private val chain = FilterChain { _, _ -> chainCalled = true }

    @AfterTest
    fun tearDown() {
        SecurityContextHolder.clearContext()
        chainCalled = false
    }

    private fun buildFilter(
        userSchedules: Map<Long, Instant> = emptyMap(),
        groupSchedules: Map<Long, List<Instant?>> = emptyMap(),
        failure: Throwable? = null,
    ) = AccountDeletionGuardFilter(
        resolveUserDeletionBlockUseCase = ResolveUserDeletionBlockUseCase(FindDeletionScheduleByUserIdGateway { userId ->
            failure?.let { Result.failure(it) } ?: Result.success(userSchedules[userId])
        }),
        resolveGroupDeletionBlockUseCase = ResolveGroupDeletionBlockUseCase(FindDeletionSchedulesByGroupIdGateway { groupId ->
            Result.success(groupSchedules[groupId].orEmpty())
        }),
        meterRegistry = meterRegistry,
    )

    private fun authenticateAsUser(userId: Long) = authenticateWithSubject(userId.toString())

    private fun authenticateWithSubject(subject: String) {
        val jwt = Jwt.withTokenValue("test-token")
            .header("alg", "none")
            .subject(subject)
            .claim("groupId", 10L)
            .claim("email", "test@controlai.dev")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .build()
        SecurityContextHolder.getContext().authentication = JwtAuthenticationToken(jwt)
    }

    private fun perform(filter: AccountDeletionGuardFilter, method: String, uri: String): MockHttpServletResponse {
        chainCalled = false
        val response = MockHttpServletResponse()
        filter.doFilter(MockHttpServletRequest(method, uri), response, chain)
        return response
    }

    @Test
    fun `blocks a user with a scheduled deletion with 423 and the date`() {
        authenticateAsUser(1L)

        val response = perform(buildFilter(userSchedules = mapOf(1L to scheduledFor)), "GET", "/purchases")

        assertFalse(chainCalled)
        assertEquals(423, response.status)
        assertEquals("application/json", response.contentType)
        assertEquals(
            """{"error":"account_deletion_scheduled","scheduledFor":"2026-10-23T15:00:00Z"}""",
            response.contentAsString,
        )
    }

    @Test
    fun `lets a user without a scheduled deletion through`() {
        authenticateAsUser(2L)

        val response = perform(buildFilter(userSchedules = mapOf(1L to scheduledFor)), "GET", "/purchases")

        assertTrue(chainCalled)
        assertEquals(200, response.status)
    }

    @Test
    fun `lets the deletion, auth, webhook and health routes through while blocked`() {
        authenticateAsUser(1L)
        val filter = buildFilter(userSchedules = mapOf(1L to scheduledFor))
        val allowed = listOf(
            "GET" to "/me/deletion",
            "DELETE" to "/me/deletion",
            "POST" to "/me/deletion",
            "POST" to "/auth/login",
            "POST" to "/auth/logout",
            "POST" to "/webhooks/kiwify",
            "GET" to "/health",
            "GET" to "/actuator/health",
        )

        allowed.forEach { (method, uri) ->
            val response = perform(filter, method, uri)

            assertTrue(chainCalled, "expected $method $uri to bypass the deletion guard")
            assertEquals(200, response.status)
        }
    }

    @Test
    fun `blocks an API key whose group has only members with a scheduled deletion`() {
        SecurityContextHolder.getContext().authentication = ApiKeyAuthentication(5L)

        val response = perform(buildFilter(groupSchedules = mapOf(5L to listOf(scheduledFor))), "POST", "/payments/notification")

        assertFalse(chainCalled)
        assertEquals(423, response.status)
    }

    @Test
    fun `lets an API key through when a partner in the group stays`() {
        SecurityContextHolder.getContext().authentication = ApiKeyAuthentication(5L)

        val response = perform(
            buildFilter(groupSchedules = mapOf(5L to listOf(scheduledFor, null))),
            "POST",
            "/payments/notification",
        )

        assertTrue(chainCalled)
        assertEquals(200, response.status)
    }

    @Test
    fun `leaves anonymous requests to the authorization rules`() {
        val response = perform(buildFilter(), "GET", "/purchases")

        assertTrue(chainCalled)
        assertEquals(200, response.status)
    }

    @Test
    fun `fails open when the deletion status cannot be read`() {
        authenticateAsUser(1L)

        val response = perform(buildFilter(failure = IllegalStateException("db down")), "GET", "/purchases")

        assertTrue(chainCalled)
        assertEquals(200, response.status)
        assertEquals(1.0, meterRegistry.counter("account.deletion.guard.failure").count())
    }

    @Test
    fun `leaves a JWT with a non-numeric subject to the rest of the chain`() {
        authenticateWithSubject("not-a-user-id")

        val response = perform(buildFilter(userSchedules = mapOf(1L to scheduledFor)), "GET", "/purchases")

        assertTrue(chainCalled)
        assertEquals(200, response.status)
    }
}
