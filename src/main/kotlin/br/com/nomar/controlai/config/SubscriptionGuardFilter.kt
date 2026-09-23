package br.com.nomar.controlai.config

import br.com.nomar.controlai.domain.billing.gateway.FindActiveSubscriptionByGroupIdGateway
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.util.AntPathMatcher
import org.springframework.web.filter.OncePerRequestFilter

private const val HTTP_STATUS_PAYMENT_REQUIRED = 402

class SubscriptionGuardFilter(
    private val findActiveSubscriptionByGroupIdGateway: FindActiveSubscriptionByGroupIdGateway,
) : OncePerRequestFilter() {

    private val log = LoggerFactory.getLogger(javaClass)
    private val pathMatcher = AntPathMatcher()

    // Account deletion must work without a subscription too (App Store 5.1.1(v), Google Play):
    // e.g. an account created by Google login that never bought a plan.
    private val subscriptionExemptPatterns = listOf("/me/deletion")

    override fun shouldNotFilter(request: HttpServletRequest): Boolean =
        (PUBLIC_ROUTE_PATTERNS + subscriptionExemptPatterns).any { pathMatcher.match(it, request.requestURI) }

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        val groupId = resolveGroupId(SecurityContextHolder.getContext().authentication)
        if (groupId == null) {
            // No resolvable group — either an anonymous/unauthenticated request, or an
            // authenticated JWT missing its groupId claim. Either way, let the request through
            // so the normal authentication/authorization path (or RequestContext) rejects it.
            chain.doFilter(request, response)
            return
        }

        val hasActiveSubscription = findActiveSubscriptionByGroupIdGateway.execute(groupId)
            .onFailure { ex -> log.error("Failed to check subscription status for group $groupId", ex) }
            .getOrNull() != null

        if (!hasActiveSubscription) {
            rejectPaymentRequired(response)
            return
        }

        chain.doFilter(request, response)
    }

    private fun resolveGroupId(authentication: Authentication?): Long? = when {
        authentication is ApiKeyAuthentication -> authentication.groupId
        authentication is JwtAuthenticationToken -> (authentication.token.claims["groupId"] as? Number)?.toLong()
        else -> null
    }

    private fun rejectPaymentRequired(response: HttpServletResponse) {
        response.status = HTTP_STATUS_PAYMENT_REQUIRED
        response.contentType = "application/json"
        response.writer.write("""{"error":"subscription_inactive"}""")
    }
}
