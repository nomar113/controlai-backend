package br.com.nomar.controlai.application.billing.entrypoint.database.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.Instant
import java.time.LocalDateTime

@Entity
@Table(name = "subscriptions")
data class SubscriptionModel(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "group_id", nullable = false, unique = true)
    val groupId: Long = 0,

    @Enumerated(EnumType.STRING)
    @Column(name = "plan", nullable = false)
    val plan: SubscriptionPlanModel = SubscriptionPlanModel.ANNUAL,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    val status: SubscriptionStatusModel = SubscriptionStatusModel.ACTIVE,

    @Column(name = "kiwify_order_id")
    val kiwifyOrderId: String? = null,

    @Column(name = "current_period_end")
    val currentPeriodEnd: Instant? = null,

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    val createdAt: LocalDateTime? = null,

    @UpdateTimestamp
    @Column(name = "updated_at")
    var updatedAt: LocalDateTime? = null,
)

enum class SubscriptionPlanModel { ANNUAL, LIFETIME, GRANDFATHERED }
enum class SubscriptionStatusModel { ACTIVE, CANCELLED, EXPIRED }
