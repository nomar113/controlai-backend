package br.com.nomar.controlai.application.auth.entrypoint.database.model

import br.com.nomar.controlai.domain.auth.entity.ApiKey
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import java.time.Instant

@Entity
@Table(name = "api_keys")
data class ApiKeyModel(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "group_id", nullable = false)
    val groupId: Long = 0,

    @Column(name = "key_hash", length = 64, nullable = false, unique = true)
    val keyHash: String = "",

    @Column(name = "label", nullable = false)
    val label: String = ApiKey.DEFAULT_LABEL,

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    val createdAt: Instant? = null,

    @Column(name = "revoked_at")
    val revokedAt: Instant? = null,
)
