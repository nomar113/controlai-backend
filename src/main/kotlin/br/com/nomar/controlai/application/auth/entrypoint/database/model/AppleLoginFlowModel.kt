package br.com.nomar.controlai.application.auth.entrypoint.database.model

import br.com.nomar.controlai.domain.auth.entity.AppleLoginPlatform
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import java.time.Instant

@Entity
@Table(name = "apple_login_flows")
data class AppleLoginFlowModel(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "nonce", length = 64, nullable = false)
    val nonce: String = "",

    @Column(name = "nonce_digest", length = 64, nullable = false, unique = true)
    val nonceDigest: String = "",

    @Column(name = "state", length = 64)
    val state: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "platform", length = 10, nullable = false)
    val platform: AppleLoginPlatform = AppleLoginPlatform.WEB,

    @Column(name = "expires_at", nullable = false)
    val expiresAt: Instant = Instant.EPOCH,

    @Column(name = "consumed_at")
    var consumedAt: Instant? = null,

    @Column(name = "handoff_code_hash", length = 64, unique = true)
    var handoffCodeHash: String? = null,

    @Column(name = "user_id")
    var userId: Long? = null,

    @Column(name = "handoff_expires_at")
    var handoffExpiresAt: Instant? = null,

    @Column(name = "handoff_consumed_at")
    var handoffConsumedAt: Instant? = null,

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    val createdAt: Instant? = null,
)
