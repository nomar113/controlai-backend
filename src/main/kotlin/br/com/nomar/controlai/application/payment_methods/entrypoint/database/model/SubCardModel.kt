package br.com.nomar.controlai.application.payment_methods.entrypoint.database.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.SQLRestriction
import org.hibernate.annotations.UpdateTimestamp
import java.time.LocalDateTime

@Entity
@Table(name = "sub_cards")
@SQLRestriction("deleted_at IS NULL")
data class SubCardModel(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "payment_method_id", nullable = false)
    val paymentMethodId: Long,

    @Column(name = "last_four_digits", length = 4, nullable = false)
    val lastFourDigits: String,

    @Column(name = "type", length = 30, nullable = false)
    val type: String,

    @Column(name = "nickname", length = 100)
    val nickname: String? = null,

    @Column(name = "dependent_name", length = 100)
    val dependentName: String? = null,

    @Column(name = "wallet_platform", length = 20)
    val walletPlatform: String? = null,

    @Column(name = "deleted_at")
    val deletedAt: LocalDateTime? = null,

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime? = null,

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime? = null,
)
