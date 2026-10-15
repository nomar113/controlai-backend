package br.com.nomar.controlai.application.auth.application

import br.com.nomar.controlai.application.auth.converter.UserIdentityConverter
import br.com.nomar.controlai.application.auth.entrypoint.database.repository.UserIdentityRepository
import br.com.nomar.controlai.domain.auth.entity.NewIdentity
import br.com.nomar.controlai.domain.auth.entity.User
import br.com.nomar.controlai.domain.auth.entity.UserIdentity
import br.com.nomar.controlai.domain.auth.gateway.CreateUserWithIdentityGateway
import br.com.nomar.controlai.domain.auth.gateway.CreateUserWithPersonalGroupGateway
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate

@Component
class CreateUserWithIdentityProvider(
    private val createUserWithPersonalGroupGateway: CreateUserWithPersonalGroupGateway,
    private val userIdentityRepository: UserIdentityRepository,
    private val userIdentityConverter: UserIdentityConverter,
    private val transactionTemplate: TransactionTemplate,
) : CreateUserWithIdentityGateway {

    override fun execute(user: User, identity: NewIdentity): Result<User> {
        return runCatching {
            // The nested create joins this transaction, so a failure in either write rolls back both
            transactionTemplate.execute {
                val created = createUserWithPersonalGroupGateway.execute(user).getOrThrow()
                userIdentityRepository.save(
                    userIdentityConverter.toModel(
                        UserIdentity(
                            userId = created.id!!,
                            provider = identity.provider,
                            subject = identity.subject,
                            appleRefreshTokenEnc = identity.appleRefreshTokenEnc,
                        ),
                    ),
                )
                created
            }!!
        }
    }
}
