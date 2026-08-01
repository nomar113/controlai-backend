package br.com.nomar.controlai.application.auth.application

import br.com.nomar.controlai.application.auth.converter.UserConverter
import br.com.nomar.controlai.application.auth.entrypoint.database.repository.UserRepository
import br.com.nomar.controlai.application.groups.entrypoint.database.model.GroupMemberModel
import br.com.nomar.controlai.application.groups.entrypoint.database.model.GroupModel
import br.com.nomar.controlai.application.groups.entrypoint.database.repository.GroupMemberRepository
import br.com.nomar.controlai.application.groups.entrypoint.database.repository.GroupRepository
import br.com.nomar.controlai.domain.auth.entity.User
import br.com.nomar.controlai.domain.auth.exception.EmailAlreadyUsedException
import br.com.nomar.controlai.domain.auth.gateway.CreateUserWithPersonalGroupGateway
import br.com.nomar.controlai.domain.auth.gateway.SeedDefaultCategoriesGateway
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate

@Component
class CreateUserWithPersonalGroupProvider(
    private val userRepository: UserRepository,
    private val groupRepository: GroupRepository,
    private val groupMemberRepository: GroupMemberRepository,
    private val userConverter: UserConverter,
    private val transactionTemplate: TransactionTemplate,
    private val seedDefaultCategoriesGateway: SeedDefaultCategoriesGateway,
) : CreateUserWithPersonalGroupGateway {

    override fun execute(user: User): Result<User> {
        return runCatching {
            try {
                transactionTemplate.execute {
                    val savedUser = userRepository.save(userConverter.toModel(user))
                    val group = groupRepository.save(GroupModel(name = savedUser.name))
                    groupMemberRepository.save(GroupMemberModel(groupId = group.id!!, userId = savedUser.id!!))
                    seedDefaultCategoriesGateway.execute(group.id!!)
                    userConverter.toEntity(savedUser)
                }!!
            } catch (e: DataIntegrityViolationException) {
                // Unique email constraint hit on a registration race
                throw EmailAlreadyUsedException()
            }
        }
    }
}
