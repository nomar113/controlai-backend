package br.com.nomar.controlai.application.purchases_invoices.entrypoint.database.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.SQLRestriction
import org.hibernate.annotations.UpdateTimestamp
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.OffsetDateTime

@Entity
@Table(name = "purchase_invoices")
@SQLRestriction("deleted_at IS NULL")
data class PurchaseInvoiceModel (
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "date")
    val date: OffsetDateTime,

    @Column(name = "merchant_name")
    val merchantName: String?,

    @Column(name = "merchant_address")
    val merchantAddress: String?,

    @Column(name = "cnpj", length = 18)
    val cnpj: String?,

    @Column(name = "total_items")
    val totalItems: Int?,

    @Column(name = "invoice_url")
    val invoiceUrl: String?,

    @Column(name = "access_key", length = 44)
    val accessKey: String?,

    @Column(name = "subtotal", precision = 10, scale = 2)
    val subtotal: BigDecimal?,

    @Column(name = "total", precision = 10, scale = 2)
    val total: BigDecimal?,

    @Column(name = "taxes", precision = 10, scale = 2)
    val taxes: BigDecimal?,

    @Column(name = "discount", precision = 10, scale = 2)
    val discount: BigDecimal?,

    @Column(name = "description")
    val description: String? = null,

    @Column(name = "deleted_at")
    val deletedAt: LocalDateTime? = null,

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime? = LocalDateTime.now(),

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime? = LocalDateTime.now()
)