package br.com.nomar.controlai.application.installments.entrypoint.database.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

@Entity
@Table(name = "installments")
data class Installment(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "group_id", nullable = false)
    val groupId: Long = 0,

    @Column(name = "parent_id", nullable = false)
    val parentId: Long,

    @Column(name = "installment_number", nullable = false)
    val installmentNumber: Int,

    @Column(name = "total_installments", nullable = false)
    val totalInstallments: Int,

    @Column(nullable = false, precision = 19, scale = 2)
    val amount: BigDecimal,

    @Column(name = "due_date", nullable = false)
    val dueDate: LocalDate,

    @Column(name = "cancelled_at")
    val cancelledAt: Instant? = null,

    @Column(name = "created_at", insertable = false, updatable = false)
    val createdAt: Instant? = null,

    @Column(name = "updated_at", insertable = false, updatable = false)
    val updatedAt: Instant? = null,
)
