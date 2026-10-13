package br.com.nomar.controlai.application.auth.converter

import br.com.nomar.controlai.application.auth.entrypoint.database.model.UserIdentityModel
import br.com.nomar.controlai.domain.auth.entity.UserIdentity
import org.springframework.stereotype.Component

@Component
class UserIdentityConverter {

    fun toEntity(model: UserIdentityModel) = UserIdentity(
        id = model.id,
        userId = model.userId,
        provider = model.provider,
        subject = model.subject,
        appleRefreshTokenEnc = model.appleRefreshTokenEnc,
        createdAt = model.createdAt,
    )

    fun toModel(entity: UserIdentity) = UserIdentityModel(
        id = entity.id,
        userId = entity.userId,
        provider = entity.provider,
        subject = entity.subject,
        appleRefreshTokenEnc = entity.appleRefreshTokenEnc,
        createdAt = entity.createdAt,
    )
}
