package br.com.nomar.controlai.application.groups.converter

import br.com.nomar.controlai.application.groups.entrypoint.database.model.GroupInviteModel
import br.com.nomar.controlai.application.groups.entrypoint.database.model.GroupInviteStatusModel
import br.com.nomar.controlai.domain.groups.entity.GroupInvite
import br.com.nomar.controlai.domain.groups.entity.InviteStatus
import org.springframework.stereotype.Component

@Component
class GroupInviteConverter {

    fun toEntity(model: GroupInviteModel) = GroupInvite(
        id = model.id,
        groupId = model.groupId,
        inviterUserId = model.inviterUserId,
        inviteeEmail = model.inviteeEmail,
        status = InviteStatus.valueOf(model.status.name),
        token = model.token,
        expiresAt = model.expiresAt,
        createdAt = model.createdAt,
    )

    fun toModel(entity: GroupInvite) = GroupInviteModel(
        id = entity.id,
        groupId = entity.groupId,
        inviterUserId = entity.inviterUserId,
        inviteeEmail = entity.inviteeEmail,
        status = GroupInviteStatusModel.valueOf(entity.status.name),
        token = entity.token,
        expiresAt = entity.expiresAt,
    )
}
