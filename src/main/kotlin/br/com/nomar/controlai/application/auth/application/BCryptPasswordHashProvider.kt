package br.com.nomar.controlai.application.auth.application

import br.com.nomar.controlai.domain.auth.gateway.PasswordHashGateway
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component

@Component
class BCryptPasswordHashProvider(
    private val passwordEncoder: PasswordEncoder,
) : PasswordHashGateway {

    override fun hash(rawPassword: String): String = passwordEncoder.encode(rawPassword)

    override fun matches(rawPassword: String, passwordHash: String): Boolean =
        passwordEncoder.matches(rawPassword, passwordHash)
}
