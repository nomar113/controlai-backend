package br.com.nomar.controlai.application.auth.converter

import br.com.nomar.controlai.application.auth.entrypoint.database.model.UserModel
import br.com.nomar.controlai.domain.auth.entity.User
import org.springframework.stereotype.Component

@Component
class UserConverter {

    fun toEntity(model: UserModel) = User(
        id = model.id,
        name = model.name,
        email = model.email,
        passwordHash = model.passwordHash,
        googleSub = model.googleSub,
    )

    fun toModel(entity: User) = UserModel(
        id = entity.id,
        name = entity.name,
        email = entity.email,
        passwordHash = entity.passwordHash,
        googleSub = entity.googleSub,
    )
}
