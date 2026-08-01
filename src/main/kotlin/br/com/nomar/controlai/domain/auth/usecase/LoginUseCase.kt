package br.com.nomar.controlai.domain.auth.usecase

import br.com.nomar.controlai.domain.auth.TokenHasher
import br.com.nomar.controlai.domain.auth.entity.AuthSession
import br.com.nomar.controlai.domain.auth.exception.InvalidCredentialsException
import br.com.nomar.controlai.domain.auth.gateway.FindUserByEmailGateway
import br.com.nomar.controlai.domain.auth.gateway.IssueTokensGateway
import br.com.nomar.controlai.domain.auth.gateway.PasswordHashGateway
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class LoginUseCase(
    private val findUserByEmailGateway: FindUserByEmailGateway,
    private val passwordHashGateway: PasswordHashGateway,
    private val issueTokensGateway: IssueTokensGateway,
    private val meterRegistry: MeterRegistry,
) {

    fun execute(email: String, password: String): Result<AuthSession> {
        return runCatching {
            val user = findUserByEmailGateway.execute(email.trim().lowercase()).getOrThrow()
            // Always run the hash comparison to keep timing similar whether or not the user exists
            val matches = passwordHashGateway.matches(password, user?.passwordHash ?: DUMMY_HASH)
            if (user?.passwordHash == null || !matches) {
                meterRegistry.counter("auth.login.failure").increment()
                // Never log the raw email on failures, only a truncated hash for correlation
                logger.info("Login failed for email hash {}", TokenHasher.sha256(email.trim().lowercase()).take(12))
                throw InvalidCredentialsException()
            }
            meterRegistry.counter("auth.login.success").increment()
            logger.info("Login succeeded for user {}", user.id)
            issueTokensGateway.execute(user).getOrThrow()
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(LoginUseCase::class.java)

        // BCrypt hash of an unused random value, only to equalize timing when the user is unknown
        private const val DUMMY_HASH = "\$2a\$10\$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy"
    }
}
