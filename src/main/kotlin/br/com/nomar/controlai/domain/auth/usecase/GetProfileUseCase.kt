package br.com.nomar.controlai.domain.auth.usecase

import br.com.nomar.controlai.domain.auth.entity.Profile
import br.com.nomar.controlai.domain.auth.gateway.FindUserByIdGateway
import br.com.nomar.controlai.domain.groups.gateway.FindUserGroupGateway
import org.springframework.stereotype.Component

@Component
class GetProfileUseCase(
    private val findUserByIdGateway: FindUserByIdGateway,
    private val findUserGroupGateway: FindUserGroupGateway,
) {

    fun execute(userId: Long): Result<Profile> {
        return runCatching {
            val user = findUserByIdGateway.execute(userId).getOrThrow()
                ?: throw NoSuchElementException("Usuario nao encontrado")
            val group = findUserGroupGateway.execute(userId).getOrThrow()
                ?: throw NoSuchElementException("Grupo do usuario nao encontrado")
            Profile(user = user, group = group)
        }
    }
}
