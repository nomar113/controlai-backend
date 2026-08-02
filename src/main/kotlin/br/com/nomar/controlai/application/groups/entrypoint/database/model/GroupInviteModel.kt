package br.com.nomar.controlai.application.groups.entrypoint.database.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import java.time.LocalDateTime

@Entity
@Table(name = "group_invites")
data class GroupInviteModel(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "group_id", nullable = false)
    val groupId: Long = 0,

    @Column(name = "inviter_user_id", nullable = false)
    val inviterUserId: Long = 0,

    @Column(name = "invitee_email", nullable = false)
    val inviteeEmail: String = "",

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    val status: GroupInviteStatusModel = GroupInviteStatusModel.PENDING,

    @Column(name = "token", nullable = false, unique = true)
    val token: String = "",

    @Column(name = "expires_at", nullable = false)
    val expiresAt: LocalDateTime = LocalDateTime.now(),

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    val createdAt: LocalDateTime? = null,
)

enum class GroupInviteStatusModel { PENDING, ACCEPTED, DECLINED, CANCELLED }
