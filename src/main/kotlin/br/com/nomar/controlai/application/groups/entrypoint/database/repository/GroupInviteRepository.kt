package br.com.nomar.controlai.application.groups.entrypoint.database.repository

import br.com.nomar.controlai.application.groups.entrypoint.database.model.GroupInviteModel
import br.com.nomar.controlai.application.groups.entrypoint.database.model.GroupInviteStatusModel
import org.springframework.data.jpa.repository.JpaRepository

interface GroupInviteRepository : JpaRepository<GroupInviteModel, Long> {
    fun findByInviteeEmailAndStatus(inviteeEmail: String, status: GroupInviteStatusModel): List<GroupInviteModel>
}
