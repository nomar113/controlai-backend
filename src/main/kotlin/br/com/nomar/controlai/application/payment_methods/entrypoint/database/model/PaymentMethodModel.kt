package br.com.nomar.controlai.application.payment_methods.entrypoint.database.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.SQLRestriction
import org.hibernate.annotations.UpdateTimestamp
import java.time.LocalDateTime

@Entity
@Table(name = "payment_methods")
@SQLRestriction("deleted_at IS NULL")
data class PaymentMethodModel(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "group_id", nullable = false)
    val groupId: Long = 0,

    @Column(name = "name", length = 100, nullable = false)
    val name: String,

    @Column(name = "type", length = 20, nullable = false)
    val type: String,

    @Column(name = "holder_id", nullable = false, insertable = false, updatable = false)
    val holderId: Long,

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "holder_id", nullable = false)
    val holder: HolderModel? = null,

    @Column(name = "brand", length = 50)
    val brand: String? = null,

    @Column(name = "closing_day")
    val closingDay: Int? = null,

    @OneToMany(mappedBy = "paymentMethodId", fetch = FetchType.EAGER)
    val subCards: List<SubCardModel> = emptyList(),

    @Column(name = "deleted_at")
    val deletedAt: LocalDateTime? = null,

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime? = null,

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime? = null,
)
