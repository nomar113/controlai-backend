package br.com.nomar.controlai.application.budget.entrypoint.database.model

import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(name = "budget_items")
data class BudgetItemModel(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "budget_id", nullable = false)
    val budget: BudgetModel? = null,

    @Column(name = "category_id", nullable = false)
    val categoryId: Long = 0,

    @Column(name = "type", length = 15, nullable = false)
    val type: String = "",

    @Column(name = "expected", nullable = false, precision = 19, scale = 2)
    val expected: BigDecimal = BigDecimal.ZERO,

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime? = null,

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime? = null,
)
