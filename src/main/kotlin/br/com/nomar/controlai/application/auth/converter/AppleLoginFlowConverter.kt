package br.com.nomar.controlai.application.auth.converter

import br.com.nomar.controlai.application.auth.entrypoint.database.model.AppleLoginFlowModel
import br.com.nomar.controlai.domain.auth.entity.AppleLoginFlow

fun AppleLoginFlowModel.toDomain(): AppleLoginFlow = AppleLoginFlow(
    id = id!!,
    nonce = nonce,
    platform = platform,
    expiresAt = expiresAt,
    consumedAt = consumedAt,
    userId = userId,
    handoffExpiresAt = handoffExpiresAt,
    handoffConsumedAt = handoffConsumedAt,
)
