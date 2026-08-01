package br.com.nomar.controlai.application.budget.entrypoint.database.model

import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.LocalDateTime

@Entity
@Table(name = "budgets")
data class BudgetModel(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "group_id", nullable = false)
    val groupId: Long = 0,

    @Column(name = "reference_month", length = 7, nullable = false)
    val yearMonth: String = "",

    @OneToMany(mappedBy = "budget", cascade = [CascadeType.ALL], orphanRemoval = true)
    val items: MutableList<BudgetItemModel> = mutableListOf(),

    @OneToMany(mappedBy = "budget", cascade = [CascadeType.ALL], orphanRemoval = true)
    val incomes: MutableList<BudgetIncomeModel> = mutableListOf(),

    @OneToMany(mappedBy = "budget", cascade = [CascadeType.ALL], orphanRemoval = true)
    val paymentPeriods: MutableList<BudgetPaymentPeriodModel> = mutableListOf(),

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime? = null,

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime? = null,
)
