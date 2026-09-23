package br.com.nomar.controlai.config

import br.com.nomar.controlai.domain.account_deletion.usecase.ResolveGroupDeletionBlockUseCase
import br.com.nomar.controlai.domain.account_deletion.usecase.ResolveUserDeletionBlockUseCase
import io.micrometer.core.instrument.MeterRegistry
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.util.AntPathMatcher
import org.springframework.web.filter.OncePerRequestFilter
import java.time.Instant

private const val HTTP_STATUS_LOCKED = 423

// Blocks the account during the deletion grace period. Login keeps working, so the user can
// still see and cancel the deletion; every other protected route answers 423 with the date.
// Runs before SubscriptionGuardFilter, so an account being deleted gets 423 rather than 402.
class AccountDeletionGuardFilter(
    private val resolveUserDeletionBlockUseCase: ResolveUserDeletionBlockUseCase,
    private val resolveGroupDeletionBlockUseCase: ResolveGroupDeletionBlockUseCase,
    private val meterRegistry: MeterRegistry,
) : OncePerRequestFilter() {

    private val log = LoggerFactory.getLogger(javaClass)
    private val pathMatcher = AntPathMatcher()

    // Public routes, plus viewing and cancelling the deletion itself. POST /me/deletion goes
    // through as well and answers 409 when the deletion is already scheduled.
    private val allowedPatterns = PUBLIC_ROUTE_PATTERNS + "/me/deletion"

    override fun shouldNotFilter(request: HttpServletRequest): Boolean =
        allowedPatterns.any { pathMatcher.match(it, request.requestURI) }

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        val authentication = SecurityContextHolder.getContext().authentication
        val scheduledFor = resolveBlock(authentication)
            // Fails open: a database error would break the request downstream anyway, and a
            // false 423 would tell the user their account is being deleted. The counter is the
            // signal that the lock stopped working.
            .onFailure { ex ->
                meterRegistry.counter("account.deletion.guard.failure").increment()
                // Principal name is the user id (JWT) or api-key-group-<id>: never an e-mail
                log.error("Failed to check account deletion status for {}", authentication?.name, ex)
            }
            .getOrNull()

        if (scheduledFor != null) {
            rejectLocked(response, scheduledFor)
            return
        }

        chain.doFilter(request, response)
    }

    // Anonymous requests are left to the normal authentication/authorization path
    private fun resolveBlock(authentication: Authentication?): Result<Instant?> = when (authentication) {
        is ApiKeyAuthentication -> resolveGroupDeletionBlockUseCase.execute(authentication.groupId)
        is JwtAuthenticationToken -> authentication.token.subject?.toLongOrNull()
            ?.let(resolveUserDeletionBlockUseCase::execute)
            ?: Result.success(null)
        else -> Result.success(null)
    }

    private fun rejectLocked(response: HttpServletResponse, scheduledFor: Instant) {
        response.status = HTTP_STATUS_LOCKED
        response.contentType = "application/json"
        // Instant.toString is ISO-8601, the same format Jackson uses in GET /me/deletion
        response.writer.write("""{"error":"account_deletion_scheduled","scheduledFor":"$scheduledFor"}""")
    }
}
