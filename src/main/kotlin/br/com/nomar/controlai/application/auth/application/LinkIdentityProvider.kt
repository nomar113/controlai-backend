package br.com.nomar.controlai.application.auth.application

import br.com.nomar.controlai.application.auth.converter.UserIdentityConverter
import br.com.nomar.controlai.application.auth.entrypoint.database.repository.UserIdentityRepository
import br.com.nomar.controlai.application.auth.entrypoint.database.repository.UserRepository
import br.com.nomar.controlai.domain.auth.entity.IdentityProvider
import br.com.nomar.controlai.domain.auth.entity.UserIdentity
import br.com.nomar.controlai.domain.auth.gateway.LinkIdentityGateway
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate

@Component
class LinkIdentityProvider(
    private val userIdentityRepository: UserIdentityRepository,
    private val userRepository: UserRepository,
    private val userIdentityConverter: UserIdentityConverter,
    private val transactionTemplate: TransactionTemplate,
) : LinkIdentityGateway {

    override fun execute(identity: UserIdentity): Result<Unit> {
        return runCatching {
            transactionTemplate.execute {
                userIdentityRepository.save(userIdentityConverter.toModel(identity))
                // users.google_sub is still written for Google so a rollback to the previous release,
                // which reads that column, keeps finding the account. Drop this once the column is removed.
                if (identity.provider == IdentityProvider.GOOGLE) {
                    val user = userRepository.findById(identity.userId).orElseThrow()
                    userRepository.save(user.copy(googleSub = identity.subject))
                }
            }
        }
    }
}
