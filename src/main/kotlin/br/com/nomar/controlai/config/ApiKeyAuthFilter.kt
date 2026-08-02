package br.com.nomar.controlai.config

import br.com.nomar.controlai.domain.auth.TokenHasher
import br.com.nomar.controlai.domain.auth.gateway.FindApiKeyByHashGateway
import io.micrometer.core.instrument.MeterRegistry
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter

class ApiKeyAuthFilter(
    private val findApiKeyByHashGateway: FindApiKeyByHashGateway,
    private val meterRegistry: MeterRegistry,
) : OncePerRequestFilter() {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun shouldNotFilter(request: HttpServletRequest): Boolean =
        !(request.method == "POST" && request.requestURI == "/payments/notification")

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        val rawKey = request.getHeader("X-Api-Key")
        if (rawKey.isNullOrBlank()) {
            rejectUnauthorized(response)
            return
        }

        val keyHash = TokenHasher.sha256(rawKey)
        val apiKey = findApiKeyByHashGateway.execute(keyHash)
            .onFailure { ex -> log.error("Failed to look up API key from database", ex) }
            .getOrNull()

        if (apiKey == null || apiKey.isRevoked()) {
            meterRegistry.counter("auth.api_key.invalid").increment()
            rejectUnauthorized(response)
            return
        }

        SecurityContextHolder.getContext().authentication = ApiKeyAuthentication(apiKey.groupId)
        chain.doFilter(request, response)
    }

    private fun rejectUnauthorized(response: HttpServletResponse) {
        response.status = HttpServletResponse.SC_UNAUTHORIZED
        response.contentType = "application/json"
        response.writer.write("""{"error":"Unauthorized","message":"Invalid or missing API key"}""")
    }
}
