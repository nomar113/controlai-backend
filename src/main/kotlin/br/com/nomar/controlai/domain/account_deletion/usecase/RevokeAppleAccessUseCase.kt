package br.com.nomar.controlai.domain.account_deletion.usecase

import br.com.nomar.controlai.domain.auth.entity.AppleRefreshCredential
import br.com.nomar.controlai.domain.auth.gateway.AppleTokenExchangeGateway
import br.com.nomar.controlai.domain.auth.gateway.FindAppleRefreshCredentialGateway
import br.com.nomar.controlai.domain.auth.gateway.TokenCipherGateway
import br.com.nomar.controlai.domain.auth.gateway.UpdateAppleRefreshCredentialGateway
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

// Revokes the ControlAI access granted in Apple when an account is deleted (TN3194). Never blocks the deletion:
// every failure is logged with the internal user id only, never the token or the e-mail.
@Component
class RevokeAppleAccessUseCase(
    private val findAppleRefreshCredentialGateway: FindAppleRefreshCredentialGateway,
    private val updateAppleRefreshCredentialGateway: UpdateAppleRefreshCredentialGateway,
    private val tokenCipherGateway: TokenCipherGateway,
    private val appleTokenExchangeGateway: AppleTokenExchangeGateway,
) {

    // Called when the user asks for the deletion. A successful revocation clears the stored token, so the
    // purge has nothing left to revoke; a failed one keeps the token, and the purge retries it at the end.
    fun revokeOnDeletionRequest(userId: Long) {
        val credential = findCredential(userId) ?: return
        appleTokenExchangeGateway.revoke(credential.refreshToken, credential.clientId)
            .onSuccess {
                updateAppleRefreshCredentialGateway.execute(userId, null)
                    .onFailure { logger.warn("Could not clear the Apple credential of user {}: {}", userId, it::class.simpleName) }
            }
            .onFailure { logger.warn("Apple access revocation failed for user {}: {}", userId, it::class.simpleName) }
    }

    // Called by the purge before it deletes the user_identities row. Null when the user has no Apple
    // identity, or when the credential cannot be read: in both cases there is nothing to revoke here.
    fun findCredential(userId: Long): AppleRefreshCredential? {
        return runCatching {
            findAppleRefreshCredentialGateway.execute(userId).getOrThrow()
                ?.let { AppleRefreshCredential.parse(tokenCipherGateway.decrypt(it)) }
        }.onFailure { logger.warn("Could not read the Apple credential of user {}: {}", userId, it::class.simpleName) }
            .getOrNull()
    }

    // Called by the purge only after it committed, so a failed purge never leaves a live account with a revoked token
    fun revoke(userId: Long, credential: AppleRefreshCredential?) {
        if (credential == null) return
        appleTokenExchangeGateway.revoke(credential.refreshToken, credential.clientId)
            .onFailure { logger.warn("Apple access revocation failed for user {}: {}", userId, it::class.simpleName) }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(RevokeAppleAccessUseCase::class.java)
    }
}
