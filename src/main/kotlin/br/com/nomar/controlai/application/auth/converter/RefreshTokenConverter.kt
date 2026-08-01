package br.com.nomar.controlai.application.auth.converter

import br.com.nomar.controlai.application.auth.entrypoint.database.model.RefreshTokenModel
import br.com.nomar.controlai.domain.auth.entity.RefreshToken
import org.springframework.stereotype.Component

@Component
class RefreshTokenConverter {

    fun toEntity(model: RefreshTokenModel) = RefreshToken(
        id = model.id,
        userId = model.userId,
        tokenHash = model.tokenHash,
        expiresAt = model.expiresAt,
        absoluteExpiresAt = model.absoluteExpiresAt,
        revokedAt = model.revokedAt,
        replacedById = model.replacedById,
    )
}
