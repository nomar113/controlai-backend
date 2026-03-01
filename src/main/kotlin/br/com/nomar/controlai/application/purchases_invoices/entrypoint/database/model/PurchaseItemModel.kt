package br.com.nomar.controlai.application.purchases_invoices.entrypoint.database.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal

@Entity
@Table(name = "purchase_items")
data class PurchaseItemModel(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "product_name", nullable = false)
    val productName: String,

    @Column(name = "code", nullable = false)
    val code: String,

    @Column(name = "quantity", precision = 10, scale = 3, nullable = false)
    val quantity: BigDecimal,

    @Column(name = "unit", nullable = false)
    val unit: String,

    @Column(name = "unit_price", precision = 10, scale = 2, nullable = false)
    val unitPrice: BigDecimal,

    @Column(name = "total_price", precision = 10, scale = 2, nullable = false)
    val totalPrice: BigDecimal,

    @Column(name = "purchase_invoice_id", nullable = false)
    val purchaseInvoiceId: Long,
)
