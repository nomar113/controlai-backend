package br.com.nomar.controlai.config

import br.com.nomar.controlai.domain.auth.gateway.FindApiKeyByHashGateway
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter
import org.springframework.security.web.SecurityFilterChain

@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val findApiKeyByHashGateway: FindApiKeyByHashGateway,
    private val meterRegistry: MeterRegistry,
) {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .cors(Customizer.withDefaults())
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            // ApiKeyAuthFilter runs before the JWT filter to handle POST /payments/notification
            .addFilterBefore(
                ApiKeyAuthFilter(findApiKeyByHashGateway, meterRegistry),
                BearerTokenAuthenticationFilter::class.java,
            )
            .authorizeHttpRequests {
                it
                    // Logout revokes the device's refresh token, so it requires a valid session
                    .requestMatchers("/auth/logout").authenticated()
                    .requestMatchers("/auth/**", "/actuator/health", "/health").permitAll()
                    .anyRequest().authenticated()
            }
            .oauth2ResourceServer { it.jwt(Customizer.withDefaults()) }
        return http.build()
    }

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()
}
