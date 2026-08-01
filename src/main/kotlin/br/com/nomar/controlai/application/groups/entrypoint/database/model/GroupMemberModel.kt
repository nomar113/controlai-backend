package br.com.nomar.controlai.application.groups.entrypoint.database.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import java.time.LocalDateTime

@Entity
@Table(name = "group_members")
data class GroupMemberModel(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "group_id", nullable = false)
    val groupId: Long = 0,

    @Column(name = "user_id", nullable = false, unique = true)
    val userId: Long = 0,

    @CreationTimestamp
    @Column(name = "joined_at", updatable = false)
    val joinedAt: LocalDateTime? = null,
)
