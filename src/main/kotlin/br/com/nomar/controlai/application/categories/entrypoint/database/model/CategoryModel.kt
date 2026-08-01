package br.com.nomar.controlai.application.categories.entrypoint.database.model

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
@Table(name = "categories")
@SQLRestriction("deleted_at IS NULL")
data class CategoryModel(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "group_id", nullable = false)
    val groupId: Long = 0,

    @Column(name = "name", length = 50, nullable = false)
    val name: String = "",

    @Column(name = "icon", length = 10, nullable = true)
    val icon: String? = null,

    @Column(name = "deleted_at")
    var deletedAt: LocalDateTime? = null,

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime? = null,

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime? = null,
)
