package br.com.nomar.controlai.config

import br.com.nomar.controlai.domain.account_deletion.usecase.ResolveGroupDeletionBlockUseCase
import br.com.nomar.controlai.domain.account_deletion.usecase.ResolveUserDeletionBlockUseCase
import br.com.nomar.controlai.domain.auth.gateway.FindApiKeyByHashGateway
import br.com.nomar.controlai.domain.billing.gateway.FindActiveSubscriptionByGroupIdGateway
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
    private val findActiveSubscriptionByGroupIdGateway: FindActiveSubscriptionByGroupIdGateway,
    private val resolveUserDeletionBlockUseCase: ResolveUserDeletionBlockUseCase,
    private val resolveGroupDeletionBlockUseCase: ResolveGroupDeletionBlockUseCase,
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
            // Both guards run after JWT/API key authentication is resolved, so they can read the
            // authenticated user and group. The deletion guard comes first: an account being
            // deleted gets 423, not 402. Anchoring the subscription guard on the deletion guard
            // (instead of both on the JWT filter) makes that order explicit.
            .addFilterAfter(
                AccountDeletionGuardFilter(resolveUserDeletionBlockUseCase, resolveGroupDeletionBlockUseCase, meterRegistry),
                BearerTokenAuthenticationFilter::class.java,
            )
            .addFilterAfter(
                SubscriptionGuardFilter(findActiveSubscriptionByGroupIdGateway),
                AccountDeletionGuardFilter::class.java,
            )
            .authorizeHttpRequests {
                it
                    // Logout revokes the device's refresh token, so it requires a valid session
                    .requestMatchers("/auth/logout").authenticated()
                    .requestMatchers("/auth/**", "/actuator/health", "/health").permitAll()
                    .requestMatchers("/webhooks/kiwify").permitAll()
                    .anyRequest().authenticated()
            }
            .oauth2ResourceServer { it.jwt(Customizer.withDefaults()) }
        return http.build()
    }

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()
}
