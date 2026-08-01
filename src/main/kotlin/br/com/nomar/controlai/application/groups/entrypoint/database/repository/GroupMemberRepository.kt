package br.com.nomar.controlai.application.groups.entrypoint.database.repository

import br.com.nomar.controlai.application.groups.entrypoint.database.model.GroupMemberModel
import org.springframework.data.jpa.repository.JpaRepository

interface GroupMemberRepository : JpaRepository<GroupMemberModel, Long> {
    fun findByUserId(userId: Long): GroupMemberModel?
}
