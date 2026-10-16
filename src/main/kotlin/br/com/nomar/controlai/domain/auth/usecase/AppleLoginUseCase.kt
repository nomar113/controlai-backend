package br.com.nomar.controlai.domain.auth.usecase

import br.com.nomar.controlai.domain.auth.entity.AppleIdentity
import br.com.nomar.controlai.domain.auth.entity.AppleLoginCommand
import br.com.nomar.controlai.domain.auth.entity.AppleRefreshCredential
import br.com.nomar.controlai.domain.auth.entity.AuthSession
import br.com.nomar.controlai.domain.auth.entity.IdentityProvider
import br.com.nomar.controlai.domain.auth.entity.NewIdentity
import br.com.nomar.controlai.domain.auth.entity.User
import br.com.nomar.controlai.domain.auth.entity.UserIdentity
import br.com.nomar.controlai.domain.auth.exception.InvalidAppleTokenException
import br.com.nomar.controlai.domain.auth.gateway.AppleIdentityVerifierGateway
import br.com.nomar.controlai.domain.auth.gateway.AppleTokenExchangeGateway
import br.com.nomar.controlai.domain.auth.gateway.CreateUserWithIdentityGateway
import br.com.nomar.controlai.domain.auth.gateway.FindUserByEmailGateway
import br.com.nomar.controlai.domain.auth.gateway.FindUserByIdentityGateway
import br.com.nomar.controlai.domain.auth.gateway.IssueTokensGateway
import br.com.nomar.controlai.domain.auth.gateway.LinkIdentityGateway
import br.com.nomar.controlai.domain.auth.gateway.TokenCipherGateway
import br.com.nomar.controlai.domain.auth.gateway.UpdateAppleRefreshCredentialGateway
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class AppleLoginUseCase(
    private val appleIdentityVerifierGateway: AppleIdentityVerifierGateway,
    private val appleTokenExchangeGateway: AppleTokenExchangeGateway,
    private val tokenCipherGateway: TokenCipherGateway,
    private val updateAppleRefreshCredentialGateway: UpdateAppleRefreshCredentialGateway,
    private val findUserByIdentityGateway: FindUserByIdentityGateway,
    private val findUserByEmailGateway: FindUserByEmailGateway,
    private val linkIdentityGateway: LinkIdentityGateway,
    private val createUserWithIdentityGateway: CreateUserWithIdentityGateway,
    private val issueTokensGateway: IssueTokensGateway,
) {

    fun execute(command: AppleLoginCommand): Result<AuthSession> {
        return resolveUser(command)
            .mapCatching { issueTokensGateway.execute(it).getOrThrow() }
            .onFailure { logger.warn("Apple login failed: {}", it::class.simpleName) }
    }

    // Finds or creates the account behind an Apple login, without issuing tokens. The web and Android
    // callback uses it so the session is only issued when the app exchanges its handoff code.
    fun resolveUser(command: AppleLoginCommand): Result<User> {
        return runCatching {
            val apple = appleIdentityVerifierGateway.verify(command.identityToken, command.nonce).getOrThrow()

            // 1. Identity already linked — each login replaces the stored refresh token with one from this code,
            // so a grant the user revoked in Apple settings is never the one ControlAI keeps
            val byIdentity = findUserByIdentityGateway.execute(IdentityProvider.APPLE, apple.subject).getOrThrow()
            if (byIdentity != null) {
                logger.info("Apple login via existing identity for user {}", byIdentity.id)
                replaceCredential(byIdentity.id!!, command, apple)
                return@runCatching byIdentity
            }

            val email = apple.email?.trim()?.lowercase()?.takeIf(String::isNotEmpty) ?: throw InvalidAppleTokenException()
            val byEmail = findUserByEmailGateway.execute(email).getOrThrow()

            // 2. Verified real e-mail matching an existing account — link. A relay address never links.
            if (byEmail != null && apple.isLinkableEmail()) {
                val encryptedRefreshToken = exchangeAndEncrypt(command, apple)
                linkIdentityGateway.execute(
                    UserIdentity(
                        userId = byEmail.id!!,
                        provider = IdentityProvider.APPLE,
                        subject = apple.subject,
                        appleRefreshTokenEnc = encryptedRefreshToken,
                    ),
                ).getOrThrow()
                logger.info("Apple linked to existing account for user {}", byEmail.id)
                return@runCatching byEmail
            }

            // E-mail in use but not verified: refuse rather than link or create a duplicate account
            if (byEmail != null) throw InvalidAppleTokenException()

            // 3. New account — created with its Apple identity and refresh token in the same transaction
            val encryptedRefreshToken = exchangeAndEncrypt(command, apple)
            val newUser = createUserWithIdentityGateway.execute(
                User(
                    name = command.fullName?.trim()?.takeIf(String::isNotEmpty) ?: email.substringBefore('@'),
                    email = email,
                ),
                NewIdentity(
                    provider = IdentityProvider.APPLE,
                    subject = apple.subject,
                    appleRefreshTokenEnc = encryptedRefreshToken,
                ),
            ).getOrThrow()
            logger.info("New account created via Apple for user {}", newUser.id)
            newUser
        }
    }

    // Best effort: the login already proved the identity with Apple's token, so a failed exchange keeps the stored one
    private fun replaceCredential(userId: Long, command: AppleLoginCommand, apple: AppleIdentity) {
        runCatching {
            val encrypted = exchangeAndEncrypt(command, apple)
            updateAppleRefreshCredentialGateway.execute(userId, encrypted).getOrThrow()
        }.onFailure { logger.warn("Could not store a new Apple credential for user {}: {}", userId, it::class.simpleName) }
    }

    private fun AppleIdentity.isLinkableEmail(): Boolean = emailVerified && !isPrivateRelay

    // Runs before any write, so an invalid authorization code never leaves a user behind
    private fun exchangeAndEncrypt(command: AppleLoginCommand, apple: AppleIdentity): String {
        val refreshToken = appleTokenExchangeGateway
            .exchangeAuthorizationCode(command.authorizationCode, apple.audience)
            .getOrThrow()
        return tokenCipherGateway.encrypt(AppleRefreshCredential(apple.audience, refreshToken).serialize())
    }

    companion object {
        private val logger = LoggerFactory.getLogger(AppleLoginUseCase::class.java)
    }
}
