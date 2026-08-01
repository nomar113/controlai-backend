package br.com.nomar.controlai.config

import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcBuilderCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import java.time.Duration
import java.time.Instant

// Picked up by component scan in test runs only: applies a real Bearer token to every
// auto-configured MockMvc request so pre-auth integration tests keep passing after the
// API lockdown. Tests that must exercise anonymous access build their own raw MockMvc.
@Configuration
class TestAuthMockMvcConfig {

    @Bean
    fun defaultBearerTokenCustomizer(jwtEncoder: JwtEncoder): MockMvcBuilderCustomizer {
        val now = Instant.now()
        val claims = JwtClaimsSet.builder()
            .subject("1")
            .issuedAt(now)
            .expiresAt(now.plus(Duration.ofHours(2)))
            .claim("groupId", 1L)
            .claim("email", "test@controlai.dev")
            .build()
        val token = jwtEncoder
            .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
            .tokenValue
        return MockMvcBuilderCustomizer { builder ->
            (builder as DefaultMockMvcBuilder)
                .defaultRequest<DefaultMockMvcBuilder>(get("/").header(HttpHeaders.AUTHORIZATION, "Bearer $token"))
        }
    }
}
