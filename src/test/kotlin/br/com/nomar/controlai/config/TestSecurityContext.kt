package br.com.nomar.controlai.config

import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import java.time.Instant

// Populates SecurityContextHolder with a fake JWT so @SpringBootTest classes that call
// providers directly (no MockMvc/HTTP request involved) satisfy JwtRequestContext.groupId.
object TestSecurityContext {

    fun authenticateAsGroup(groupId: Long = 1L, userId: Long = 1L, email: String = "test@controlai.dev") {
        val jwt = Jwt.withTokenValue("test-token")
            .header("alg", "none")
            .subject(userId.toString())
            .claim("groupId", groupId)
            .claim("email", email)
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .build()
        SecurityContextHolder.getContext().authentication = JwtAuthenticationToken(jwt)
    }

    fun clear() {
        SecurityContextHolder.clearContext()
    }
}
