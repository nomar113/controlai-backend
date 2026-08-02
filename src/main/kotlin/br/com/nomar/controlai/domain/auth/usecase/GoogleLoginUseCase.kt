package br.com.nomar.controlai.domain.auth.usecase

import br.com.nomar.controlai.domain.auth.entity.AuthSession
import br.com.nomar.controlai.domain.auth.entity.User
import br.com.nomar.controlai.domain.auth.gateway.CreateUserWithPersonalGroupGateway
import br.com.nomar.controlai.domain.auth.gateway.FindUserByEmailGateway
import br.com.nomar.controlai.domain.auth.gateway.FindUserByGoogleSubGateway
import br.com.nomar.controlai.domain.auth.gateway.GoogleTokenVerifierGateway
import br.com.nomar.controlai.domain.auth.gateway.IssueTokensGateway
import br.com.nomar.controlai.domain.auth.gateway.LinkGoogleSubToUserGateway
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class GoogleLoginUseCase(
    private val googleTokenVerifierGateway: GoogleTokenVerifierGateway,
    private val findUserByGoogleSubGateway: FindUserByGoogleSubGateway,
    private val findUserByEmailGateway: FindUserByEmailGateway,
    private val linkGoogleSubToUserGateway: LinkGoogleSubToUserGateway,
    private val createUserWithPersonalGroupGateway: CreateUserWithPersonalGroupGateway,
    private val issueTokensGateway: IssueTokensGateway,
) {

    fun execute(idToken: String): Result<AuthSession> {
        return runCatching {
            val googleUser = googleTokenVerifierGateway.verify(idToken).getOrThrow()

            // 1. Already linked to this google_sub — fastest path
            val byGoogleSub = findUserByGoogleSubGateway.execute(googleUser.sub).getOrThrow()
            if (byGoogleSub != null) {
                logger.info("Google login via existing google_sub for user {}", byGoogleSub.id)
                return@runCatching issueTokensGateway.execute(byGoogleSub).getOrThrow()
            }

            // 2. Same email exists with password login (RF-1.5) — link google_sub
            val byEmail = findUserByEmailGateway.execute(googleUser.email.trim().lowercase()).getOrThrow()
            if (byEmail != null) {
                val linked = linkGoogleSubToUserGateway.execute(byEmail.id!!, googleUser.sub).getOrThrow()
                logger.info("Google linked to existing email-password account for user {}", linked.id)
                return@runCatching issueTokensGateway.execute(linked).getOrThrow()
            }

            // 3. New account — create with google_sub, no password
            val newUser = createUserWithPersonalGroupGateway.execute(
                User(
                    name = googleUser.name,
                    email = googleUser.email.trim().lowercase(),
                    googleSub = googleUser.sub,
                ),
            ).getOrThrow()
            logger.info("New account created via Google for user {}", newUser.id)
            issueTokensGateway.execute(newUser).getOrThrow()
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(GoogleLoginUseCase::class.java)
    }
}
