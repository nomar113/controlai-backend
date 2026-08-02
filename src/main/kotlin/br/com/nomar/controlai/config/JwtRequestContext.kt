package br.com.nomar.controlai.config

import br.com.nomar.controlai.domain.auth.RequestContext
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.stereotype.Component
import org.springframework.web.context.annotation.RequestScope

@Component
@RequestScope
class JwtRequestContext : RequestContext {

    override val userId: Long
        get() = jwt().subject.toLong()

    override val groupId: Long
        get() = when (val auth = SecurityContextHolder.getContext().authentication) {
            is ApiKeyAuthentication -> auth.groupId
            is JwtAuthenticationToken -> (auth.token.claims["groupId"] as? Number)?.toLong()
                ?: throw IllegalStateException("JWT has no groupId claim")
            else -> throw IllegalStateException("No authenticated principal in the current request")
        }

    override val email: String
        get() = jwt().getClaimAsString("email")
            ?: throw IllegalStateException("JWT has no email claim")

    private fun jwt(): Jwt =
        (SecurityContextHolder.getContext().authentication as? JwtAuthenticationToken)?.token
            ?: throw IllegalStateException("No authenticated JWT in the current request")
}
