package br.com.nomar.controlai.domain.auth.usecase

import br.com.nomar.controlai.domain.auth.entity.AuthSession
import br.com.nomar.controlai.domain.auth.entity.IdentityProvider
import br.com.nomar.controlai.domain.auth.entity.NewIdentity
import br.com.nomar.controlai.domain.auth.entity.User
import br.com.nomar.controlai.domain.auth.entity.UserIdentity
import br.com.nomar.controlai.domain.auth.gateway.CreateUserWithIdentityGateway
import br.com.nomar.controlai.domain.auth.gateway.FindUserByEmailGateway
import br.com.nomar.controlai.domain.auth.gateway.FindUserByIdentityGateway
import br.com.nomar.controlai.domain.auth.gateway.GoogleTokenVerifierGateway
import br.com.nomar.controlai.domain.auth.gateway.IssueTokensGateway
import br.com.nomar.controlai.domain.auth.gateway.LinkIdentityGateway
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class GoogleLoginUseCase(
    private val googleTokenVerifierGateway: GoogleTokenVerifierGateway,
    private val findUserByIdentityGateway: FindUserByIdentityGateway,
    private val findUserByEmailGateway: FindUserByEmailGateway,
    private val linkIdentityGateway: LinkIdentityGateway,
    private val createUserWithIdentityGateway: CreateUserWithIdentityGateway,
    private val issueTokensGateway: IssueTokensGateway,
) {

    fun execute(idToken: String): Result<AuthSession> {
        return runCatching {
            val googleUser = googleTokenVerifierGateway.verify(idToken).getOrThrow()

            // 1. Already linked to this Google account — fastest path
            val byIdentity = findUserByIdentityGateway.execute(IdentityProvider.GOOGLE, googleUser.sub).getOrThrow()
            if (byIdentity != null) {
                logger.info("Google login via existing identity for user {}", byIdentity.id)
                return@runCatching issueTokensGateway.execute(byIdentity).getOrThrow()
            }

            // 2. Same email exists with password login (RF-1.5) — link the Google identity
            val byEmail = findUserByEmailGateway.execute(googleUser.email.trim().lowercase()).getOrThrow()
            if (byEmail != null) {
                linkIdentityGateway.execute(
                    UserIdentity(userId = byEmail.id!!, provider = IdentityProvider.GOOGLE, subject = googleUser.sub),
                ).getOrThrow()
                logger.info("Google linked to existing email-password account for user {}", byEmail.id)
                return@runCatching issueTokensGateway.execute(byEmail).getOrThrow()
            }

            // 3. New account — create without password, with its Google identity in the same transaction
            val newUser = createUserWithIdentityGateway.execute(
                User(
                    name = googleUser.name,
                    email = googleUser.email.trim().lowercase(),
                    googleSub = googleUser.sub,
                ),
                NewIdentity(provider = IdentityProvider.GOOGLE, subject = googleUser.sub),
            ).getOrThrow()
            logger.info("New account created via Google for user {}", newUser.id)
            issueTokensGateway.execute(newUser).getOrThrow()
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(GoogleLoginUseCase::class.java)
    }
}
