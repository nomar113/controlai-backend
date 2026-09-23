package br.com.nomar.controlai.application.groups.entrypoint.database.repository

import br.com.nomar.controlai.application.groups.entrypoint.database.model.GroupInviteModel
import br.com.nomar.controlai.application.groups.entrypoint.database.model.GroupInviteStatusModel
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional

interface GroupInviteRepository : JpaRepository<GroupInviteModel, Long> {
    fun findByInviteeEmailAndStatus(inviteeEmail: String, status: GroupInviteStatusModel): List<GroupInviteModel>

    // Invites in `from` status sent by the user or addressed to their e-mail move to `to`
    @Modifying
    @Transactional
    @Query(
        "UPDATE GroupInviteModel i SET i.status = :to " +
            "WHERE i.status = :from AND (i.inviterUserId = :userId OR i.inviteeEmail = :email)",
    )
    fun updateStatusSentOrReceivedBy(
        @Param("userId") userId: Long,
        @Param("email") email: String,
        @Param("from") from: GroupInviteStatusModel,
        @Param("to") to: GroupInviteStatusModel,
    ): Int
}
