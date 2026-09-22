package br.com.nomar.controlai.application.groups.application

import br.com.nomar.controlai.application.groups.entrypoint.database.model.GroupModel
import br.com.nomar.controlai.application.groups.entrypoint.database.repository.GroupRepository
import br.com.nomar.controlai.domain.auth.gateway.SeedDefaultCategoriesGateway
import br.com.nomar.controlai.domain.groups.gateway.CreatePersonalGroupGateway
import br.com.nomar.controlai.domain.payment_methods.gateway.SeedDefaultHolderGateway
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class CreatePersonalGroupProvider(
    private val groupRepository: GroupRepository,
    private val seedDefaultCategoriesGateway: SeedDefaultCategoriesGateway,
    private val seedDefaultHolderGateway: SeedDefaultHolderGateway,
) : CreatePersonalGroupGateway {

    @Transactional
    override fun execute(userId: Long, groupName: String): Result<Long> {
        return runCatching {
            val group = groupRepository.save(GroupModel(name = groupName))
            seedDefaultCategoriesGateway.execute(group.id!!)
            seedDefaultHolderGateway.execute(group.id!!, groupName)
            group.id!!
        }
    }
}
