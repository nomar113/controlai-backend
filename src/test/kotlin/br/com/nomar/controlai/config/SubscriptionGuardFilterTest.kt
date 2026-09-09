package br.com.nomar.controlai.config

import br.com.nomar.controlai.domain.billing.entity.Subscription
import br.com.nomar.controlai.domain.billing.entity.SubscriptionPlan
import br.com.nomar.controlai.domain.billing.entity.SubscriptionStatus
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

class SubscriptionGuardFilterTest {

    private var chainCalled = false
    private val chain = FilterChain { _, _ -> chainCalled = true }

    @AfterTest
    fun tearDown() {
        SecurityContextHolder.clearContext()
        chainCalled = false
    }

    private fun buildFilter(subscriptionsByGroup: Map<Long, Subscription?>) =
        SubscriptionGuardFilter(
            findActiveSubscriptionByGroupIdGateway = { groupId -> Result.success(subscriptionsByGroup[groupId]) },
        )

    private fun authenticateAsGroup(groupId: Long) {
        val jwt = Jwt.withTokenValue("test-token")
            .header("alg", "none")
            .subject("1")
            .claim("groupId", groupId)
            .claim("email", "test@controlai.dev")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .build()
        SecurityContextHolder.getContext().authentication = JwtAuthenticationToken(jwt)
    }

    @Test
    fun `allows request when group has an active subscription`() {
        val groupId = 1L
        authenticateAsGroup(groupId)
        val filter = buildFilter(
            mapOf(
                groupId to Subscription(
                    groupId = groupId,
                    plan = SubscriptionPlan.GRANDFATHERED,
                    status = SubscriptionStatus.ACTIVE,
                ),
            ),
        )
        val request = MockHttpServletRequest("GET", "/purchases")
        val response = MockHttpServletResponse()

        filter.doFilter(request, response, chain)

        assertTrue(chainCalled)
        assertEquals(200, response.status)
    }

    @Test
    fun `blocks with 402 when group has no subscription row`() {
        val groupId = 2L
        authenticateAsGroup(groupId)
        val filter = buildFilter(emptyMap())
        val request = MockHttpServletRequest("GET", "/purchases")
        val response = MockHttpServletResponse()

        filter.doFilter(request, response, chain)

        assertFalse(chainCalled)
        assertEquals(402, response.status)
        assertEquals("""{"error":"subscription_inactive"}""", response.contentAsString)
    }

    @Test
    fun `allows request authenticated via API key when group has an active subscription`() {
        val groupId = 4L
        SecurityContextHolder.getContext().authentication = ApiKeyAuthentication(groupId)
        val filter = buildFilter(
            mapOf(
                groupId to Subscription(groupId = groupId, plan = SubscriptionPlan.ANNUAL, status = SubscriptionStatus.ACTIVE),
            ),
        )
        val request = MockHttpServletRequest("POST", "/payments/notification")
        val response = MockHttpServletResponse()

        filter.doFilter(request, response, chain)

        assertTrue(chainCalled)
        assertEquals(200, response.status)
    }

    @Test
    fun `blocks with 402 when group authenticated via API key has no subscription`() {
        val groupId = 5L
        SecurityContextHolder.getContext().authentication = ApiKeyAuthentication(groupId)
        val filter = buildFilter(emptyMap())
        val request = MockHttpServletRequest("POST", "/payments/notification")
        val response = MockHttpServletResponse()

        filter.doFilter(request, response, chain)

        assertFalse(chainCalled)
        assertEquals(402, response.status)
    }

    @Test
    fun `blocks with 402 when group's subscription is cancelled`() {
        val groupId = 3L
        authenticateAsGroup(groupId)
        // FindActiveSubscriptionByGroupIdGateway already filters non-ACTIVE rows to null upstream,
        // so a cancelled subscription looks the same as "no subscription" from the filter's view.
        val filter = buildFilter(mapOf(groupId to null))
        val request = MockHttpServletRequest("GET", "/purchases")
        val response = MockHttpServletResponse()

        filter.doFilter(request, response, chain)

        assertFalse(chainCalled)
        assertEquals(402, response.status)
    }

    @Test
    fun `bypasses public routes without checking subscription`() {
        val filter = buildFilter(emptyMap())
        val publicUris = listOf("/auth/login", "/webhooks/kiwify", "/health", "/actuator/health")

        publicUris.forEach { uri ->
            chainCalled = false
            val request = MockHttpServletRequest("GET", uri)
            val response = MockHttpServletResponse()

            filter.doFilter(request, response, chain)

            assertTrue(chainCalled, "expected $uri to bypass the subscription check")
            assertEquals(200, response.status)
        }
    }
}
