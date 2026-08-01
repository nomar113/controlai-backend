package br.com.nomar.controlai.application.auth.entrypoint.database.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import java.time.Instant
import java.time.LocalDateTime

@Entity
@Table(name = "refresh_tokens")
data class RefreshTokenModel(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "user_id", nullable = false)
    val userId: Long = 0,

    @Column(name = "token_hash", length = 64, nullable = false, unique = true)
    val tokenHash: String = "",

    @Column(name = "expires_at", nullable = false)
    val expiresAt: Instant = Instant.EPOCH,

    @Column(name = "absolute_expires_at", nullable = false)
    val absoluteExpiresAt: Instant = Instant.EPOCH,

    @Column(name = "revoked_at")
    var revokedAt: Instant? = null,

    @Column(name = "replaced_by_id")
    var replacedById: Long? = null,

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    val createdAt: LocalDateTime? = null,
)
