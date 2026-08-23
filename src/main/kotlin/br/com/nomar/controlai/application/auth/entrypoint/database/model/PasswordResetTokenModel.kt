package br.com.nomar.controlai.application.auth.entrypoint.database.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import java.time.Instant

@Entity
@Table(name = "password_reset_tokens")
data class PasswordResetTokenModel(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "user_id", nullable = false)
    val userId: Long = 0,

    @Column(name = "token_hash", length = 64, nullable = false, unique = true)
    val tokenHash: String = "",

    @Column(name = "expires_at", nullable = false)
    val expiresAt: Instant = Instant.EPOCH,

    @Column(name = "used_at")
    var usedAt: Instant? = null,

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    val createdAt: Instant? = null,
)
