package br.com.nomar.controlai.application.auth.entrypoint.database.model

import br.com.nomar.controlai.domain.auth.entity.IdentityProvider
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
@Table(name = "user_identities")
data class UserIdentityModel(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "user_id", nullable = false)
    val userId: Long = 0,

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", length = 20, nullable = false)
    val provider: IdentityProvider = IdentityProvider.GOOGLE,

    @Column(name = "subject", nullable = false)
    val subject: String = "",

    @Column(name = "apple_refresh_token_enc", length = 512)
    val appleRefreshTokenEnc: String? = null,

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    val createdAt: Instant? = null,
)
